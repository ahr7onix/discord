package com.chronos.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chronos.app.chronosContainer
import java.util.concurrent.TimeUnit

/**
 * Periodic safety net (spec §2 "WorkManager para tarefas auxiliares", §7
 * reliability). Exact alarms can be dropped by the OS under battery
 * restrictions or OEM killing; this worker re-arms the full alarm set on a
 * daily cadence so a lost alarm self-heals without the user doing anything.
 *
 * It complements, and does not replace, [BootReceiver] (which reacts to boot
 * and time/zone changes immediately) and the self-advancing [AlarmReceiver].
 */
class MaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        applicationContext.chronosContainer.scheduler.rescheduleAll()
        Result.success()
    } catch (t: Throwable) {
        // Transient failures (DB busy, etc.) get retried with backoff.
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "chronos.maintenance.reschedule"

        /** Enqueue the daily maintenance job, keeping any already scheduled. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
