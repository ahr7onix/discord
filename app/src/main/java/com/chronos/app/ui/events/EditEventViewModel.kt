package com.chronos.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronos.app.data.repository.ChronosRepository
import com.chronos.app.domain.model.Category
import com.chronos.app.domain.model.EditScope
import com.chronos.app.domain.model.EndMode
import com.chronos.app.domain.model.Event
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.model.MissingDayPolicy
import com.chronos.app.domain.model.MonthlyMode
import com.chronos.app.domain.model.Priority
import com.chronos.app.domain.model.RecurrenceRule
import com.chronos.app.domain.model.ReminderLead
import com.chronos.app.domain.model.SeriesState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Editable form for creating or editing an event and its recurrence. */
data class EditFormState(
    val isNew: Boolean = true,
    val title: String = "",
    val description: String = "",
    val categoryId: Long? = null,
    val priority: Priority = Priority.NORMAL,
    val date: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val hasEndTime: Boolean = false,
    val endTime: LocalTime = LocalTime.of(10, 0),
    val frequency: Frequency = Frequency.NONE,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val monthlyMode: MonthlyMode = MonthlyMode.DAY_OF_MONTH,
    val weekOfMonth: Int = 1,
    val missingDayPolicy: MissingDayPolicy = MissingDayPolicy.SKIP,
    val endMode: EndMode = EndMode.NEVER,
    val until: LocalDate? = null,
    val count: Int = 10,
    val reminders: List<ReminderLead> = listOf(ReminderLead.MIN_15),
    val state: SeriesState = SeriesState.ACTIVE,
    val saving: Boolean = false,
) {
    val titleValid: Boolean get() = title.isNotBlank()
    val isRecurring: Boolean get() = frequency != Frequency.NONE
}

class EditEventViewModel(
    private val repository: ChronosRepository,
    private val eventId: Long?,
    initialEpochDay: Long?,
) : ViewModel() {

    private var original: Event? = null

    private val _form = MutableStateFlow(
        EditFormState(
            isNew = eventId == null,
            date = initialEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now(),
        )
    )
    val form = _form.asStateFlow()

    val categories = repository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Category>())

    init {
        if (eventId != null) viewModelScope.launch { load(eventId) }
    }

    private suspend fun load(id: Long) {
        val e = repository.getEvent(id) ?: return
        original = e
        val r = e.rule
        _form.value = EditFormState(
            isNew = false,
            title = e.title,
            description = e.description.orEmpty(),
            categoryId = e.categoryId,
            priority = e.priority,
            date = r.anchorDate,
            startTime = e.startTime,
            hasEndTime = e.endTime != null,
            endTime = e.endTime ?: LocalTime.of(10, 0),
            frequency = r.frequency,
            interval = r.interval,
            daysOfWeek = r.daysOfWeek,
            monthlyMode = r.monthlyMode,
            weekOfMonth = r.weekOfMonth,
            missingDayPolicy = r.missingDayPolicy,
            endMode = r.endMode,
            until = r.until,
            count = r.count ?: 10,
            reminders = e.reminders,
            state = e.state,
        )
    }

    fun update(block: (EditFormState) -> EditFormState) { _form.value = block(_form.value) }

    fun toggleWeekday(day: DayOfWeek) = update { f ->
        f.copy(daysOfWeek = if (day in f.daysOfWeek) f.daysOfWeek - day else f.daysOfWeek + day)
    }

    fun toggleReminder(lead: ReminderLead) = update { f ->
        val present = f.reminders.any { it.minutesBefore == lead.minutesBefore }
        val next = if (present) f.reminders.filterNot { it.minutesBefore == lead.minutesBefore }
        else (f.reminders + lead).sortedByDescending { it.minutesBefore }
        f.copy(reminders = next.ifEmpty { listOf(ReminderLead.AT_TIME) })
    }

    private fun buildRule(f: EditFormState): RecurrenceRule {
        if (f.frequency == Frequency.NONE) return RecurrenceRule.single(f.date)
        return RecurrenceRule(
            frequency = f.frequency,
            interval = f.interval.coerceAtLeast(1),
            daysOfWeek = if (f.frequency == Frequency.WEEKLY) f.daysOfWeek else emptySet(),
            monthlyMode = f.monthlyMode,
            dayOfMonth = f.date.dayOfMonth,
            weekOfMonth = f.weekOfMonth,
            positionalDay = f.date.dayOfWeek,
            anchorDate = f.date,
            endMode = f.endMode,
            until = if (f.endMode == EndMode.UNTIL_DATE) f.until else null,
            count = if (f.endMode == EndMode.COUNT) f.count.coerceAtLeast(1) else null,
            missingDayPolicy = f.missingDayPolicy,
        )
    }

    private fun buildEvent(f: EditFormState): Event = Event(
        id = original?.id ?: 0,
        title = f.title.trim(),
        description = f.description.trim().ifBlank { null },
        categoryId = f.categoryId,
        priority = f.priority,
        startTime = f.startTime,
        endTime = if (f.hasEndTime) f.endTime else null,
        rule = buildRule(f),
        reminders = f.reminders,
        state = f.state,
        createdAt = original?.createdAt ?: System.currentTimeMillis(),
    )

    /**
     * Persists the form. For an edit of a recurring series, [scope] decides
     * whether it touches one occurrence, this-and-future, or all (§5).
     */
    fun save(scope: EditScope = EditScope.ALL, onDone: (Long) -> Unit) {
        val f = _form.value
        if (!f.titleValid) return
        _form.value = f.copy(saving = true)
        viewModelScope.launch {
            val event = buildEvent(f)
            val id = when {
                original == null || !event.isRecurring || scope == EditScope.ALL ->
                    repository.saveEvent(event)
                else -> repository.applyEdit(original!!, event, scope, f.date)
            }
            onDone(id)
        }
    }
}
