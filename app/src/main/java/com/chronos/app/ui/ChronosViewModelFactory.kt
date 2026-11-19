package com.chronos.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.chronos.app.AppContainer
import com.chronos.app.ui.calendar.CalendarViewModel
import com.chronos.app.ui.events.EditEventViewModel
import com.chronos.app.ui.events.EventDetailViewModel
import com.chronos.app.ui.events.EventsViewModel
import com.chronos.app.ui.home.HomeViewModel
import com.chronos.app.ui.settings.SettingsViewModel
import com.chronos.app.ui.statistics.StatisticsViewModel

/**
 * Builds every screen ViewModel from the shared [AppContainer]. A thin manual
 * factory avoids a DI framework while keeping ViewModels constructor-injected
 * and testable. Args-carrying ViewModels (edit screen) use [forEditEvent].
 */
class ChronosViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
            HomeViewModel(container.repository) as T
        modelClass.isAssignableFrom(CalendarViewModel::class.java) ->
            CalendarViewModel(container.repository) as T
        modelClass.isAssignableFrom(EventsViewModel::class.java) ->
            EventsViewModel(container.repository) as T
        modelClass.isAssignableFrom(StatisticsViewModel::class.java) ->
            StatisticsViewModel(container.repository) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container.repository, container.settings, container.scheduler, container.backupManager) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }

    fun forEditEvent(eventId: Long?, initialEpochDay: Long?): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EditEventViewModel(container.repository, eventId, initialEpochDay) as T
        }

    fun forEventDetail(eventId: Long): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                EventDetailViewModel(container.repository, eventId) as T
        }
}
