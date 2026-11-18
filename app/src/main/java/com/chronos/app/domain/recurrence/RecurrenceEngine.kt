package com.chronos.app.domain.recurrence

import com.chronos.app.domain.model.EndMode
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.model.MissingDayPolicy
import com.chronos.app.domain.model.MonthlyMode
import com.chronos.app.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * Computes the dates a [RecurrenceRule] fires on. Pure, deterministic and
 * zone-agnostic: it reasons entirely in wall-clock [LocalDate]s and never
 * touches alarms, storage, time zones or the current clock. The scheduler
 * layer turns a chosen date into an absolute instant.
 *
 * The engine produces occurrences lazily and on demand; infinite series are
 * never expanded into storage (spec §6). All queries are bounded, so even a
 * NEVER-ending rule terminates.
 *
 * Edge cases handled (spec §6–§7):
 *  - Months lacking the target day (e.g. day 31) → [MissingDayPolicy].
 *  - Positional "5th weekday" that a month lacks → that month is skipped.
 *  - Feb 29 yearly → only leap years fire (SKIP) or clamp to Feb 28 (LAST_DAY).
 *  - Interval counting is anchored, so pausing/resuming never drifts the phase.
 */
object RecurrenceEngine {

    /** Upper bound on raw iterations — a guard against a non-advancing bug. */
    private const val SAFETY_CAP = 200_000

    /**
     * All occurrences in ascending order from [RecurrenceRule.anchorDate],
     * honoring the rule's end mode. May be infinite (NEVER) — always consume
     * through a bounded operator.
     */
    fun occurrences(rule: RecurrenceRule): Sequence<LocalDate> {
        val raw = rawDates(rule).take(SAFETY_CAP)
        return when (rule.endMode) {
            EndMode.NEVER -> raw
            EndMode.UNTIL_DATE -> raw.takeWhile { !it.isAfter(rule.until) }
            EndMode.COUNT -> raw.take(rule.count ?: 0)
        }
    }

    /** Occurrences intersecting the inclusive window [from, to]. Always finite. */
    fun occurrencesInRange(
        rule: RecurrenceRule,
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> {
        if (from.isAfter(to)) return emptyList()
        return occurrences(rule)
            .dropWhile { it.isBefore(from) }
            .takeWhile { !it.isAfter(to) }
            .toList()
    }

    /** First occurrence on or after [onOrAfter], or null if the series ended. */
    fun nextOccurrence(rule: RecurrenceRule, onOrAfter: LocalDate): LocalDate? =
        occurrences(rule).firstOrNull { !it.isBefore(onOrAfter) }

    /** Total number of occurrences for a finite series, or null if infinite. */
    fun totalCount(rule: RecurrenceRule): Int? =
        if (rule.endMode == EndMode.NEVER) null else occurrences(rule).count()

    // ---- raw candidate generation (no end-mode trimming) --------------------

    private fun rawDates(rule: RecurrenceRule): Sequence<LocalDate> = when (rule.frequency) {
        Frequency.NONE -> sequenceOf(rule.anchorDate)
        Frequency.DAILY -> daily(rule)
        Frequency.WEEKLY -> weekly(rule)
        Frequency.MONTHLY -> monthly(rule)
        Frequency.YEARLY -> yearly(rule)
    }

    private fun daily(rule: RecurrenceRule): Sequence<LocalDate> {
        val step = rule.interval.toLong()
        return generateSequence(0L) { it + 1 }
            .map { rule.anchorDate.plusDays(it * step) }
    }

    private fun weekly(rule: RecurrenceRule): Sequence<LocalDate> {
        val active = rule.daysOfWeek
            .ifEmpty { setOf(rule.anchorDate.dayOfWeek) }
            .sortedBy { it.value } // Monday..Sunday ascending
        // ISO Monday of the anchor's week — a fixed phase reference for the
        // every-N-weeks interval, independent of locale first-day-of-week.
        val anchorWeekStart = rule.anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val step = rule.interval.toLong()
        return sequence {
            var block = 0L
            while (true) {
                val weekStart = anchorWeekStart.plusWeeks(block * step)
                for (dow in active) {
                    val date = weekStart.plusDays((dow.value - DayOfWeek.MONDAY.value).toLong())
                    if (!date.isBefore(rule.anchorDate)) yield(date)
                }
                block++
            }
        }
    }

    private fun monthly(rule: RecurrenceRule): Sequence<LocalDate> {
        val anchorMonth = YearMonth.from(rule.anchorDate)
        val step = rule.interval.toLong()
        return sequence {
            var k = 0L
            while (true) {
                val ym = anchorMonth.plusMonths(k * step)
                val date = when (rule.monthlyMode) {
                    MonthlyMode.DAY_OF_MONTH -> resolveDayOfMonth(ym, rule.dayOfMonth, rule.missingDayPolicy)
                    MonthlyMode.DAY_OF_WEEK_POSITION ->
                        nthWeekdayOfMonth(ym, rule.positionalDay, rule.weekOfMonth)
                }
                if (date != null && !date.isBefore(rule.anchorDate)) yield(date)
                k++
            }
        }
    }

    private fun yearly(rule: RecurrenceRule): Sequence<LocalDate> {
        val month = rule.anchorDate.monthValue
        val day = rule.anchorDate.dayOfMonth
        val step = rule.interval
        return sequence {
            var k = 0
            while (true) {
                val year = rule.anchorDate.year + k * step
                val ym = YearMonth.of(year, month)
                val date = resolveDayOfMonth(ym, day, rule.missingDayPolicy)
                if (date != null && !date.isBefore(rule.anchorDate)) yield(date)
                k++
            }
        }
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * The [day] of month [ym], applying [policy] when the day does not exist
     * (e.g. day 31 of a 30-day month, or Feb 29 of a common year).
     */
    private fun resolveDayOfMonth(
        ym: YearMonth,
        day: Int,
        policy: MissingDayPolicy,
    ): LocalDate? = when {
        day <= ym.lengthOfMonth() -> ym.atDay(day)
        policy == MissingDayPolicy.LAST_DAY -> ym.atEndOfMonth()
        else -> null
    }

    /**
     * Date of the [week]-th [dow] in [ym]. [week] is 1..4, or -1 for "last".
     * Returns null when the month has no such occurrence (e.g. no 5th Monday).
     */
    private fun nthWeekdayOfMonth(ym: YearMonth, dow: DayOfWeek, week: Int): LocalDate? {
        if (week == -1) return ym.atDay(1).with(TemporalAdjusters.lastInMonth(dow))
        val candidate = ym.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(week, dow))
        // dayOfWeekInMonth rolls into a neighboring month when the ordinal is
        // out of range; reject anything that left the target month.
        return if (YearMonth.from(candidate) == ym) candidate else null
    }
}
