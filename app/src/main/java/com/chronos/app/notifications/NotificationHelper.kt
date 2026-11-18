package com.chronos.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chronos.app.MainActivity
import com.chronos.app.R
import java.time.LocalDate

/**
 * Builds notification channels and posts occurrence alerts with Complete and
 * Snooze actions (spec §7). All posting is permission-aware: on API 33+ it
 * checks POST_NOTIFICATIONS and no-ops if denied, never crashing.
 */
object NotificationHelper {

    const val CHANNEL_REMINDERS = "chronos.reminders"

    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_desc)
            enableVibration(true)
        }
        mgr.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

    fun show(
        context: Context,
        eventId: Long,
        occurrenceDate: LocalDate,
        title: String,
        body: String?,
    ) {
        if (!canPost(context)) return

        val notificationId = stableNotificationId(eventId, occurrenceDate)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_EVENT_ID, eventId)
        }
        val contentPending = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val completePending = actionPending(
            context, NotificationActionReceiver.ACTION_COMPLETE, eventId, occurrenceDate,
        )
        val snoozePending = actionPending(
            context, NotificationActionReceiver.ACTION_SNOOZE, eventId, occurrenceDate,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .apply { if (!body.isNullOrBlank()) setContentText(body) }
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .addAction(0, context.getString(R.string.action_complete), completePending)
            .addAction(0, context.getString(R.string.action_snooze), snoozePending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun dismiss(context: Context, eventId: Long, occurrenceDate: LocalDate) {
        NotificationManagerCompat.from(context).cancel(stableNotificationId(eventId, occurrenceDate))
    }

    private fun actionPending(
        context: Context,
        action: String,
        eventId: Long,
        date: LocalDate,
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationActionReceiver.EXTRA_EPOCH_DAY, date.toEpochDay())
        }
        // Distinct request code per (action, event, date) so actions don't collide.
        val code = (action.hashCode() * 31 + stableNotificationId(eventId, date))
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Stable per-occurrence notification id so updates/dismissals match. */
    fun stableNotificationId(eventId: Long, date: LocalDate): Int {
        var h = eventId
        h = h * 31 + date.toEpochDay()
        return (h and 0x7FFFFFFF).toInt()
    }
}
