package com.chronos.app.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A recurrence specification. The engine stores only this rule and computes
 * occurrences on demand — it never materializes an infinite series into the
 * database (spec §6).
 *
 * All day/date reasoning is done in wall-clock (zone-agnostic) space. The
 * scheduler resolves a chosen occurrence to an absolute instant using the
 * device's current zone at scheduling time, so a timezone or DST change is
 * absorbed by rescheduling rather than by rewriting stored data (spec §7).
 *
 * @param frequency base unit.
 * @param interval every-N units (1 = every unit, 2 = every other, 15 with
 *        DAILY = fortnightly-by-days, etc.). Must be >= 1.
 * @param daysOfWeek for WEEKLY: the weekdays that fire each active week.
 *        Empty means "same weekday as [anchorDate]".
 * @param monthlyMode for MONTHLY: fixed day vs positional.
 * @param dayOfMonth for MONTHLY DAY_OF_MONTH: 1..31.
 * @param weekOfMonth for MONTHLY DAY_OF_WEEK_POSITION: 1..4, or -1 for last.
 * @param positionalDay for MONTHLY DAY_OF_WEEK_POSITION: which weekday.
 * @param anchorDate the series origin (DTSTART date). Interval counting and
 *        positional math are measured from here.
 * @param endMode how the series terminates.
 * @param until inclusive last date (EndMode.UNTIL_DATE).
 * @param count number of occurrences (EndMode.COUNT).
 * @param missingDayPolicy behavior for non-existent days (e.g. day 31).
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val monthlyMode: MonthlyMode = MonthlyMode.DAY_OF_MONTH,
    val dayOfMonth: Int = 1,
    val weekOfMonth: Int = 1,
    val positionalDay: DayOfWeek = DayOfWeek.MONDAY,
    val anchorDate: LocalDate,
    val endMode: EndMode = EndMode.NEVER,
    val until: LocalDate? = null,
    val count: Int? = null,
    val missingDayPolicy: MissingDayPolicy = MissingDayPolicy.SKIP,
) {
    init {
        require(interval >= 1) { "interval must be >= 1, was $interval" }
        if (endMode == EndMode.COUNT) {
            require((count ?: 0) >= 1) { "COUNT end mode needs count >= 1" }
        }
        if (endMode == EndMode.UNTIL_DATE) {
            require(until != null) { "UNTIL_DATE end mode needs an until date" }
        }
    }

    val isRecurring: Boolean get() = frequency != Frequency.NONE

    companion object {
        /** A one-shot "event" on [date] — convenience for single events. */
        fun single(date: LocalDate) = RecurrenceRule(
            frequency = Frequency.NONE,
            anchorDate = date,
            endMode = EndMode.COUNT,
            count = 1,
        )
    }
}
