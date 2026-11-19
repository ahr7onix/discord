package com.chronos.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.domain.model.Category
import com.chronos.app.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EventsUiState(
    val events: List<Event> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    val query: String = "",
    val categoryFilter: Long? = null,
)

class EventsViewModel(private val repository: ChronosRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<Long?>(null)
    val query = _query.asStateFlow()
    val categoryFilter = _categoryFilter.asStateFlow()

    val state = combine(
        repository.events,
        repository.categories,
        _query,
        _categoryFilter,
    ) { events, categories, query, catFilter ->
        val filtered = events.asSequence()
            .filter { catFilter == null || it.categoryId == catFilter }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.description?.contains(query, ignoreCase = true) == true }
            .sortedBy { it.title.lowercase() }
            .toList()
        EventsUiState(
            events = filtered,
            categories = categories.associateBy { it.id },
            query = query,
            categoryFilter = catFilter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUiState())

    fun setQuery(q: String) { _query.value = q }
    fun setCategoryFilter(id: Long?) { _categoryFilter.value = id }

    fun delete(event: Event) = viewModelScope.launch { repository.deleteEvent(event.id) }
    fun pause(event: Event) = viewModelScope.launch { repository.pauseSeries(event.id, null, null) }
    fun resume(event: Event) = viewModelScope.launch { repository.resumeSeries(event.id) }
    fun cancel(event: Event) = viewModelScope.launch { repository.cancelSeries(event.id) }
}
