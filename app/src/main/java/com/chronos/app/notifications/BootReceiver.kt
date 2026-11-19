package com.chronos.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chronos.app.chronosContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rebuilds all alarms after the device reboots, the app is updated, or the
 * clock/timezone changes — alarms do not survive those events, and a zone
 * change means every trigger instant must be recomputed (spec §7, §12).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }
        val pending = goAsync()
        val container = context.chronosContainer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationHelper.ensureChannels(context)
                container.scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
