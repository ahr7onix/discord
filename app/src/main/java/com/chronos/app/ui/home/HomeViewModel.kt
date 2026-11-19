package com.chronos.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.domain.model.EventOccurrence
import com.chronos.app.domain.model.OccurrenceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val today: LocalDate = LocalDate.now(),
    val todayOccurrences: List<EventOccurrence> = emptyList(),
    val upcoming: List<EventOccurrence> = emptyList(),
    val pendingCount: Int = 0,
    val overdueCount: Int = 0,
    val completedTodayCount: Int = 0,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repository: ChronosRepository) : ViewModel() {

    private val today = LocalDate.now()

    val state = combine(
        repository.occurrences(today, today),
        repository.occurrences(today.plusDays(1), today.plusDays(7)),
    ) { todays, upcoming ->
        HomeUiState(
            today = today,
            todayOccurrences = todays,
            upcoming = upcoming.take(10),
            pendingCount = todays.count { it.status == OccurrenceStatus.PENDING && !it.isOverdue },
            overdueCount = todays.count { it.isOverdue },
            completedTodayCount = todays.count { it.status == OccurrenceStatus.COMPLETED },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun complete(occurrence: EventOccurrence) = viewModelScope.launch {
        repository.markStatus(occurrence.event, occurrence.date, OccurrenceStatus.COMPLETED)
    }

    fun undoComplete(occurrence: EventOccurrence) = viewModelScope.launch {
        repository.clearStatus(occurrence.event.id, occurrence.date)
    }

    fun skip(occurrence: EventOccurrence) = viewModelScope.launch {
        repository.skipOccurrence(occurrence.event.id, occurrence.date)
    }
}
