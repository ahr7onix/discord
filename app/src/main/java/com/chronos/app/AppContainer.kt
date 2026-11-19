package com.chronos.app

import android.content.Context
import com.chronos.app.data.database.ChronosDatabase
import com.chronos.app.data.repository.BackupManager
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.data.repository.SettingsRepository
import com.chronos.app.domain.scheduler.EventScheduler
import com.chronos.app.notifications.AlarmSchedulerImpl

/**
 * Manual dependency container — a single wiring point for the database,
 * repositories and scheduler. Kept deliberately lightweight (no DI framework)
 * and lazy so receivers and the Application share one graph.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: ChronosDatabase by lazy { ChronosDatabase.get(appContext) }

    val scheduler: EventScheduler by lazy {
        AlarmSchedulerImpl(
            context = appContext,
            eventDao = database.eventDao(),
            notificationDao = database.notificationDao(),
        )
    }

    val repository: ChronosRepository by lazy {
        ChronosRepository(
            eventDao = database.eventDao(),
            categoryDao = database.categoryDao(),
            historyDao = database.historyDao(),
            scheduler = scheduler,
        )
    }

    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val backupManager: BackupManager by lazy {
        BackupManager(
            eventDao = database.eventDao(),
            categoryDao = database.categoryDao(),
            historyDao = database.historyDao(),
            notificationDao = database.notificationDao(),
            scheduler = scheduler,
        )
    }
}
