package com.chronos.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.repository.AppSettings
import com.chronos.app.data.repository.BackupManager
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.data.repository.SettingsRepository
import com.chronos.app.data.repository.ThemeMode
import com.chronos.app.domain.model.Category
import com.chronos.app.domain.scheduler.EventScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek

/** One-shot UI events (snackbars) surfaced from async settings actions. */
sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
}

class SettingsViewModel(
    private val repository: ChronosRepository,
    private val settingsRepo: SettingsRepository,
    private val scheduler: EventScheduler,
    private val backupManager: BackupManager,
) : ViewModel() {

    val settings = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val categories = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Category>())

    private val _events = MutableStateFlow<SettingsEvent?>(null)
    val events = _events.asStateFlow()
    fun consumeEvent() { _events.value = null }

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepo.setTheme(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { settingsRepo.setDynamicColor(on) }
    fun setClock24h(on: Boolean) = viewModelScope.launch { settingsRepo.setClock24h(on) }
    fun setFirstDayOfWeek(day: DayOfWeek) = viewModelScope.launch { settingsRepo.setFirstDayOfWeek(day) }
    fun setDefaultLead(min: Int) = viewModelScope.launch { settingsRepo.setDefaultLead(min) }
    fun setSound(on: Boolean) = viewModelScope.launch { settingsRepo.setSound(on) }
    fun setVibration(on: Boolean) = viewModelScope.launch { settingsRepo.setVibration(on) }

    fun addCategory(name: String, colorArgb: Int) = viewModelScope.launch {
        if (name.isNotBlank()) repository.saveCategory(Category(name = name.trim(), colorArgb = colorArgb))
    }

    fun deleteCategory(id: Long) = viewModelScope.launch { repository.deleteCategory(id) }

    fun exportBackup(out: OutputStream) = viewModelScope.launch {
        runCatching { out.use { backupManager.exportTo(it) } }
            .onSuccess { _events.value = SettingsEvent.Message("Backup exportado") }
            .onFailure { _events.value = SettingsEvent.Message("Falha ao exportar: ${it.message}") }
    }

    fun importBackup(input: InputStream) = viewModelScope.launch {
        runCatching { input.use { backupManager.importFrom(it) } }
            .onSuccess { _events.value = SettingsEvent.Message("Backup restaurado") }
            .onFailure { _events.value = SettingsEvent.Message("Falha ao restaurar: ${it.message}") }
    }
}
