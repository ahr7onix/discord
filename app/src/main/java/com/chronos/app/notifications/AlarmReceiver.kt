package com.chronos.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chronos.app.chronosContainer
import com.chronos.app.domain.model.SeriesState
import com.chronos.app.domain.recurrence.RecurrenceDescriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Fires when an occurrence's reminder is due. Posts the notification, then
 * re-arms the event so the next occurrence is scheduled — this is what keeps
 * an infinite series alive without pre-materializing alarms (spec §6–§7).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        val epochDay = intent.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE)
        if (eventId < 0 || epochDay == Long.MIN_VALUE) return
        val date = LocalDate.ofEpochDay(epochDay)

        val pending = goAsync()
        val container = context.chronosContainer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val event = container.repository.getEvent(eventId)
                if (event != null && event.state != SeriesState.CANCELLED && !event.isPausedOn(date)) {
                    val body = buildString {
                        event.description?.takeIf { it.isNotBlank() }?.let { append(it) }
                        if (event.isRecurring) {
                            if (isNotEmpty()) append(" · ")
                            append(RecurrenceDescriber.describe(event.rule))
                        }
                    }.ifBlank { null }
                    NotificationHelper.show(context, eventId, date, event.title, body)
                    // Advance the horizon to the following occurrence.
                    container.scheduler.reschedule(event)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EPOCH_DAY = "epoch_day"
        const val EXTRA_LEAD = "lead"
    }
}
