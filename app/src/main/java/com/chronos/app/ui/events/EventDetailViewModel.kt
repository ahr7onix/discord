package com.chronos.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.domain.model.Category
import com.chronos.app.domain.model.Event
import com.chronos.app.domain.model.EventOccurrence
import com.chronos.app.domain.model.OccurrenceStatus
import com.chronos.app.domain.model.SeriesState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EventDetailUiState(
    val event: Event? = null,
    val category: Category? = null,
    val upcoming: List<EventOccurrence> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModel(
    private val repository: ChronosRepository,
    private val eventId: Long,
) : ViewModel() {

    private val today = LocalDate.now()

    val state = combine(
        repository.observeEvent(eventId),
        repository.occurrencesForEvent(eventId, today, today.plusMonths(6)),
        repository.categories,
    ) { event, upcoming, categories ->
        EventDetailUiState(
            event = event,
            category = event?.categoryId?.let { id -> categories.firstOrNull { it.id == id } },
            upcoming = upcoming.take(8),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUiState())

    fun complete(occ: EventOccurrence) = viewModelScope.launch {
        repository.markStatus(occ.event, occ.date, OccurrenceStatus.COMPLETED)
    }

    fun undoComplete(occ: EventOccurrence) = viewModelScope.launch {
        repository.clearStatus(occ.event.id, occ.date)
    }

    fun skip(occ: EventOccurrence) = viewModelScope.launch {
        repository.skipOccurrence(occ.event.id, occ.date)
    }

    fun pauseResume(event: Event) = viewModelScope.launch {
        if (event.state == SeriesState.PAUSED) repository.resumeSeries(event.id)
        else repository.pauseSeries(event.id, null, null)
    }

    fun cancel(event: Event) = viewModelScope.launch { repository.cancelSeries(event.id) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        repository.deleteEvent(eventId)
        onDone()
    }
}
