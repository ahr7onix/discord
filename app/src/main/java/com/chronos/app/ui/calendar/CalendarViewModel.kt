package com.chronos.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.domain.model.EventOccurrence
import com.chronos.app.domain.model.OccurrenceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarMode { DAY, WEEK, MONTH }

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repository: ChronosRepository) : ViewModel() {

    private val _mode = MutableStateFlow(CalendarMode.MONTH)
    val mode = _mode.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth = _visibleMonth.asStateFlow()

    /** Occurrences grouped by date for the currently visible window. */
    val occurrencesByDate = _visibleMonth.flatMapLatest { month ->
        // Expand a month plus padding so week/day views near edges stay filled.
        val from = month.atDay(1).minusDays(7)
        val to = month.atEndOfMonth().plusDays(7)
        repository.occurrences(from, to).map { list -> list.groupBy { it.date } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val selectedDayOccurrences = _selectedDate.flatMapLatest { date ->
        repository.occurrences(date, date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMode(mode: CalendarMode) { _mode.value = mode }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _visibleMonth.value = YearMonth.from(date)
    }

    fun nextMonth() { _visibleMonth.value = _visibleMonth.value.plusMonths(1) }
    fun previousMonth() { _visibleMonth.value = _visibleMonth.value.minusMonths(1) }

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
