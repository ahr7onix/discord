package com.chronos.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chronos.app.data.dao.EventDao
import com.chronos.app.data.dao.NotificationDao
import com.chronos.app.data.entities.NotificationEntity
import com.chronos.app.data.repository.Mappers
import com.chronos.app.domain.model.Event
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.recurrence.RecurrenceEngine
import com.chronos.app.domain.scheduler.EventScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * [EventScheduler] backed by AlarmManager.
 *
 * To scale to infinite series and stay within OS alarm limits, only the
 * *next* occurrence that still has a future reminder is armed per event. When
 * an alarm fires, [AlarmReceiver] calls [reschedule] again, which arms the
 * following occurrence — a self-advancing horizon of exactly one event ahead.
 *
 * Trigger instants are resolved from wall-clock time through the device's
 * current zone ([ZoneId.systemDefault]); a DST gap rolls forward to the next
 * valid instant and a DST overlap takes the earlier offset (java.time
 * defaults). A time/zone change triggers [rescheduleAll] via BootReceiver.
 */
class AlarmSchedulerImpl(
    private val context: Context,
    private val eventDao: EventDao,
    private val notificationDao: NotificationDao,
) : EventScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    override suspend fun reschedule(event: Event) {
        cancelAll(event.id)
        if (event.state == com.chronos.app.domain.model.SeriesState.CANCELLED) return

        val armed = computeNextArmed(event) ?: return
        val exact = canScheduleExact()
        armed.forEach { a ->
            val pending = alarmPending(a.eventId, a.occurrenceDate, a.leadMinutes, a.requestCode)
            scheduleAt(a.triggerAtMillis, pending, exact)
            notificationDao.insert(
                NotificationEntity(
                    eventId = a.eventId,
                    occurrenceDate = a.occurrenceDate,
                    leadMinutes = a.leadMinutes,
                    triggerAtMillis = a.triggerAtMillis,
                    requestCode = a.requestCode,
                    exact = exact,
                )
            )
        }
    }

    override suspend fun cancelAll(eventId: Long) {
        notificationDao.getByEvent(eventId).forEach { n ->
            val pending = alarmPending(n.eventId, n.occurrenceDate, n.leadMinutes, n.requestCode)
            alarmManager.cancel(pending)
        }
        notificationDao.deleteByEvent(eventId)
    }

    override suspend fun rescheduleAll() {
        // Drop every stale alarm, then re-arm from the live event set.
        notificationDao.getAll().forEach { n ->
            alarmManager.cancel(alarmPending(n.eventId, n.occurrenceDate, n.leadMinutes, n.requestCode))
        }
        notificationDao.deleteAll()
        eventDao.getActiveWithRules().forEach { row ->
            reschedule(Mappers.toDomain(row))
        }
    }

    // ---- internals ----------------------------------------------------------

    private data class Armed(
        val eventId: Long,
        val occurrenceDate: LocalDate,
        val leadMinutes: Int,
        val triggerAtMillis: Long,
        val requestCode: Int,
    )

    /**
     * Finds the soonest occurrence with at least one future reminder and
     * returns an [Armed] entry for each of that occurrence's future leads.
     */
    private suspend fun computeNextArmed(event: Event): List<Armed>? {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)

        // Hourly events fire on a sub-daily cadence; compute the next instant
        // directly and arm one alarm (AlarmReceiver re-arms the following one).
        if (event.rule.frequency == Frequency.HOURLY) {
            return computeNextHourly(event, zone, now)
        }

        // Generous horizon so even multi-year intervals get armed eventually.
        val horizonEnd = today.plusYears(6)

        val skips = eventDao.getExceptions(event.id)
            .filter { it.type == "SKIP" }
            .map { it.originalDate }
            .toSet()

        val dates = event.occurrenceDatesBetween(today, horizonEnd).filterNot { it in skips }
        for (date in dates) {
            val leads = event.reminders.mapNotNull { lead ->
                val wall = event.startAt(date).minusMinutes(lead.minutesBefore.toLong())
                val trigger = wall.atZone(zone).toInstant().toEpochMilli()
                if (trigger > now) {
                    Armed(
                        eventId = event.id,
                        occurrenceDate = date,
                        leadMinutes = lead.minutesBefore,
                        triggerAtMillis = trigger,
                        requestCode = requestCode(event.id, date, lead.minutesBefore),
                    )
                } else null
            }
            if (leads.isNotEmpty()) return leads
        }
        return null
    }

    /** Next hourly trigger strictly after now, skipping paused days. */
    private fun computeNextHourly(event: Event, zone: ZoneId, now: Long): List<Armed>? {
        var cursor = LocalDateTime.now(zone)
        repeat(500) {
            val next = RecurrenceEngine.nextHourlyTrigger(event.rule, event.startTime, cursor)
                ?: return null
            val date = next.toLocalDate()
            val triggerMillis = next.atZone(zone).toInstant().toEpochMilli()
            when {
                triggerMillis <= now -> cursor = next.plusSeconds(1)
                event.isPausedOn(date) -> cursor = date.plusDays(1).atStartOfDay()
                else -> {
                    val reqCode = ((event.id * 31 + triggerMillis / 3_600_000L) and 0x7FFFFFFF).toInt()
                    return listOf(Armed(event.id, date, 0, triggerMillis, reqCode))
                }
            }
        }
        return null
    }

    private fun scheduleAt(triggerAtMillis: Long, pending: PendingIntent, exact: Boolean) {
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            // Degrade gracefully when exact alarms are not permitted (§7).
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun alarmPending(
        eventId: Long,
        date: LocalDate,
        lead: Int,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(AlarmReceiver.EXTRA_EPOCH_DAY, date.toEpochDay())
            putExtra(AlarmReceiver.EXTRA_LEAD, lead)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        /** Deterministic, stable id so re-arming the same logical alarm dedupes. */
        fun requestCode(eventId: Long, date: LocalDate, lead: Int): Int {
            var h = eventId
            h = h * 31 + date.toEpochDay()
            h = h * 31 + lead
            return (h and 0x7FFFFFFF).toInt()
        }
    }
}
