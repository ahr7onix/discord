package com.chronos.app.domain.scheduler

import com.chronos.app.domain.model.Event

/**
 * Abstraction over the OS alarm layer. The repository depends on this, not on
 * AlarmManager, so the data layer stays free of Android framework types and
 * remains unit-testable. Implemented by the notifications module.
 */
interface EventScheduler {
    /** (Re)arm all upcoming alarms for one event; clears its stale alarms first. */
    suspend fun reschedule(event: Event)

    /** Cancel every armed alarm for an event (e.g. on delete/cancel). */
    suspend fun cancelAll(eventId: Long)

    /** Rebuild the full alarm set — after boot, time/zone change, or bulk edits. */
    suspend fun rescheduleAll()

    /** Whether the OS currently allows exact alarms (API 31+ user toggle). */
    fun canScheduleExact(): Boolean
}

/** No-op scheduler for tests and previews. */
object NoopScheduler : EventScheduler {
    override suspend fun reschedule(event: Event) {}
    override suspend fun cancelAll(eventId: Long) {}
    override suspend fun rescheduleAll() {}
    override fun canScheduleExact(): Boolean = true
}
