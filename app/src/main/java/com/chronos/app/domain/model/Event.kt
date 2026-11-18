package com.chronos.app.domain.model

import com.chronos.app.domain.recurrence.RecurrenceEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Running state of a series. */
enum class SeriesState { ACTIVE, PAUSED, CANCELLED }

/** A notification lead time, in minutes before the occurrence start. */
@JvmInline
value class ReminderLead(val minutesBefore: Int) {
    init { require(minutesBefore >= 0) { "lead must be >= 0" } }
    companion object {
        val AT_TIME = ReminderLead(0)
        val MIN_5 = ReminderLead(5)
        val MIN_15 = ReminderLead(15)
        val MIN_30 = ReminderLead(30)
        val HOUR_1 = ReminderLead(60)
        val DAY_1 = ReminderLead(60 * 24)
    }
}

/**
 * The event aggregate: the user-facing definition plus its recurrence rule,
 * reminder lead times and series state. Concrete instances are produced on
 * demand by [occurrencesBetween]; they are never stored ahead of time.
 */
data class Event(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val categoryId: Long? = null,
    val priority: Priority = Priority.NORMAL,
    val startTime: LocalTime,
    val endTime: LocalTime? = null,
    val rule: RecurrenceRule,
    val reminders: List<ReminderLead> = listOf(ReminderLead.AT_TIME),
    val state: SeriesState = SeriesState.ACTIVE,
    val pauseStart: LocalDate? = null,
    val pauseEnd: LocalDate? = null,
    val colorArgb: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isRecurring: Boolean get() = rule.isRecurring

    /** True if [date] falls inside an active pause window. */
    fun isPausedOn(date: LocalDate): Boolean {
        if (state != SeriesState.PAUSED) return false
        val from = pauseStart ?: return true
        val afterStart = !date.isBefore(from)
        val beforeEnd = pauseEnd?.let { !date.isAfter(it) } ?: true
        return afterStart && beforeEnd
    }

    /**
     * Raw occurrence dates in [from, to] from the engine, filtered by series
     * state and pause window. Exceptions (moved/skipped single occurrences)
     * are applied by the repository layer, which owns that data.
     */
    fun occurrenceDatesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (state == SeriesState.CANCELLED) return emptyList()
        return RecurrenceEngine.occurrencesInRange(rule, from, to)
            .filterNot { isPausedOn(it) }
    }

    /** Wall-clock start datetime for a given occurrence date. */
    fun startAt(date: LocalDate): LocalDateTime = date.atTime(startTime)

    /** Wall-clock end datetime, or null when the event has no end time. */
    fun endAt(date: LocalDate): LocalDateTime? = endTime?.let {
        // An end earlier than start means the event spills past midnight.
        val end = date.atTime(it)
        if (it.isBefore(startTime)) end.plusDays(1) else end
    }
}

/**
 * A concrete, expanded instance of an [Event] on a specific date, with its
 * resolved status. Produced for calendar/agenda rendering.
 */
data class EventOccurrence(
    val event: Event,
    val date: LocalDate,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val overriddenStart: LocalDateTime? = null,
) {
    val start: LocalDateTime get() = overriddenStart ?: event.startAt(date)
    val end: LocalDateTime? get() = event.endAt(date)
    val isOverdue: Boolean
        get() = status == OccurrenceStatus.PENDING && start.isBefore(LocalDateTime.now())
}
