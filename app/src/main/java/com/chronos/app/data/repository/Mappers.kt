package com.chronos.app.data.repository

import com.chronos.app.data.entities.CategoryEntity
import com.chronos.app.data.entities.EventEntity
import com.chronos.app.data.entities.EventWithRule
import com.chronos.app.data.entities.RecurrenceRuleEntity
import com.chronos.app.domain.model.Category
import com.chronos.app.domain.model.EndMode
import com.chronos.app.domain.model.Event
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.model.MissingDayPolicy
import com.chronos.app.domain.model.MonthlyMode
import com.chronos.app.domain.model.Priority
import com.chronos.app.domain.model.RecurrenceRule
import com.chronos.app.domain.model.ReminderLead
import com.chronos.app.domain.model.SeriesState

/** Entity ↔ domain mapping. Enums are stored by name; reminders as CSV. */
object Mappers {

    fun toDomain(ewr: EventWithRule): Event {
        val e = ewr.event
        val r = ewr.rule
        val rule = if (r != null) toDomainRule(r) else RecurrenceRule.single(
            // Defensive fallback: a missing rule row degrades to a single event.
            date = java.time.LocalDate.now()
        )
        return Event(
            id = e.id,
            title = e.title,
            description = e.description,
            categoryId = e.categoryId,
            priority = Priority.valueOf(e.priority),
            startTime = e.startTime,
            endTime = e.endTime,
            rule = rule,
            reminders = parseReminders(e.reminders),
            state = SeriesState.valueOf(e.state),
            pauseStart = e.pauseStart,
            pauseEnd = e.pauseEnd,
            colorArgb = e.colorArgb,
            createdAt = e.createdAt,
            updatedAt = e.updatedAt,
        )
    }

    private fun toDomainRule(r: RecurrenceRuleEntity) = RecurrenceRule(
        frequency = Frequency.valueOf(r.frequency),
        interval = r.interval,
        daysOfWeek = r.daysOfWeek,
        monthlyMode = MonthlyMode.valueOf(r.monthlyMode),
        dayOfMonth = r.dayOfMonth,
        weekOfMonth = r.weekOfMonth,
        positionalDay = r.positionalDay,
        anchorDate = r.anchorDate,
        endMode = EndMode.valueOf(r.endMode),
        until = r.until,
        count = r.count,
        missingDayPolicy = MissingDayPolicy.valueOf(r.missingDayPolicy),
    )

    fun toEntity(event: Event): EventEntity = EventEntity(
        id = event.id,
        title = event.title,
        description = event.description,
        categoryId = event.categoryId,
        priority = event.priority.name,
        startTime = event.startTime,
        endTime = event.endTime,
        reminders = event.reminders.joinToString(",") { it.minutesBefore.toString() },
        state = event.state.name,
        pauseStart = event.pauseStart,
        pauseEnd = event.pauseEnd,
        colorArgb = event.colorArgb,
        createdAt = event.createdAt,
        updatedAt = event.updatedAt,
    )

    fun toRuleEntity(eventId: Long, rule: RecurrenceRule) = RecurrenceRuleEntity(
        eventId = eventId,
        frequency = rule.frequency.name,
        interval = rule.interval,
        daysOfWeek = rule.daysOfWeek,
        monthlyMode = rule.monthlyMode.name,
        dayOfMonth = rule.dayOfMonth,
        weekOfMonth = rule.weekOfMonth,
        positionalDay = rule.positionalDay,
        anchorDate = rule.anchorDate,
        endMode = rule.endMode.name,
        until = rule.until,
        count = rule.count,
        missingDayPolicy = rule.missingDayPolicy.name,
    )

    fun toDomainCategory(c: CategoryEntity) = Category(c.id, c.name, c.colorArgb, c.isDefault)

    fun toEntity(c: Category) = CategoryEntity(c.id, c.name, c.colorArgb, c.isDefault)

    private fun parseReminders(csv: String): List<ReminderLead> =
        csv.split(",").filter { it.isNotBlank() }.map { ReminderLead(it.trim().toInt()) }
            .ifEmpty { listOf(ReminderLead.AT_TIME) }
}
