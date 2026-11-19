package com.chronos.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chronos.app.chronosContainer
import com.chronos.app.domain.model.OccurrenceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Handles the Complete and Snooze actions on a posted reminder (spec §7). */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
        if (eventId < 0 || epochDay == Long.MIN_VALUE) return
        val date = LocalDate.ofEpochDay(epochDay)

        when (intent.action) {
            ACTION_COMPLETE -> complete(context, eventId, date)
            ACTION_SNOOZE -> snooze(context, eventId, date)
        }
    }

    private fun complete(context: Context, eventId: Long, date: LocalDate) {
        val pending = goAsync()
        val container = context.chronosContainer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.repository.getEvent(eventId)?.let {
                    container.repository.markStatus(it, date, OccurrenceStatus.COMPLETED)
                }
                NotificationHelper.dismiss(context, eventId, date)
            } finally {
                pending.finish()
            }
        }
    }

    private fun snooze(context: Context, eventId: Long, date: LocalDate) {
        NotificationHelper.dismiss(context, eventId, date)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(AlarmReceiver.EXTRA_EPOCH_DAY, date.toEpochDay())
            putExtra(AlarmReceiver.EXTRA_LEAD, 0)
        }
        val code = AlarmSchedulerImpl.requestCode(eventId, date, SNOOZE_LEAD_SENTINEL)
        val pendingIntent = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val exact = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
        if (exact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.chronos.app.action.COMPLETE"
        const val ACTION_SNOOZE = "com.chronos.app.action.SNOOZE"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EPOCH_DAY = "epoch_day"
        private const val SNOOZE_MINUTES = 10L
        private const val SNOOZE_LEAD_SENTINEL = -999
    }
}
