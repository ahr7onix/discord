package com.chronos.app.recurrence

import com.chronos.app.domain.model.EndMode
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.model.MissingDayPolicy
import com.chronos.app.domain.model.MonthlyMode
import com.chronos.app.domain.model.RecurrenceRule
import com.chronos.app.domain.recurrence.RecurrenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Covers the mandatory recurrence cases from spec §12, with the §6–§7 edge
 * cases (day 31, leap years, positional months) pinned to explicit expected
 * output so behavior is predictable and regression-proof.
 */
class RecurrenceEngineTest {

    private fun take(rule: RecurrenceRule, n: Int): List<LocalDate> =
        RecurrenceEngine.occurrences(rule).take(n).toList()

    @Test
    fun singleEvent_hasExactlyOneOccurrence() {
        val rule = RecurrenceRule.single(LocalDate.of(2026, 3, 10))
        val all = RecurrenceEngine.occurrences(rule).toList()
        assertEquals(listOf(LocalDate.of(2026, 3, 10)), all)
    }

    @Test
    fun daily_everyDay() {
        val rule = RecurrenceRule(Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1))
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3),
            ),
            take(rule, 3),
        )
    }

    @Test
    fun daily_everyFifteenDays_fortnightlyByDays() {
        val rule = RecurrenceRule(Frequency.DAILY, interval = 15, anchorDate = LocalDate.of(2026, 1, 1))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 31)),
            take(rule, 3),
        )
    }

    @Test
    fun weekly_sameWeekdayAsAnchor() {
        // 2026-01-05 is a Monday.
        val rule = RecurrenceRule(Frequency.WEEKLY, anchorDate = LocalDate.of(2026, 1, 5))
        val dates = take(rule, 3)
        assertTrue(dates.all { it.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(LocalDate.of(2026, 1, 12), dates[1])
    }

    @Test
    fun weekly_specificDays_monWedFri() {
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            anchorDate = LocalDate.of(2026, 1, 5), // Monday
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 9),
                LocalDate.of(2026, 1, 12),
            ),
            take(rule, 4),
        )
    }

    @Test
    fun weekly_everyTwoWeeks() {
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY, interval = 2,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            anchorDate = LocalDate.of(2026, 1, 5),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 19), LocalDate.of(2026, 2, 2)),
            take(rule, 3),
        )
    }

    @Test
    fun monthly_dayOfMonth_normal() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY, monthlyMode = MonthlyMode.DAY_OF_MONTH,
            dayOfMonth = 10, anchorDate = LocalDate.of(2026, 1, 10),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10)),
            take(rule, 3),
        )
    }

    @Test
    fun monthly_day31_skipsMonthsWithoutIt() {
        // Day 31: Feb, Apr, Jun... have no 31st → skipped (SKIP policy).
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY, monthlyMode = MonthlyMode.DAY_OF_MONTH,
            dayOfMonth = 31, anchorDate = LocalDate.of(2026, 1, 31),
            missingDayPolicy = MissingDayPolicy.SKIP,
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 5, 31), LocalDate.of(2026, 7, 31),
            ),
            take(rule, 4),
        )
    }

    @Test
    fun monthly_day31_lastDayPolicy_clampsToEndOfMonth() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY, monthlyMode = MonthlyMode.DAY_OF_MONTH,
            dayOfMonth = 31, anchorDate = LocalDate.of(2026, 1, 31),
            missingDayPolicy = MissingDayPolicy.LAST_DAY,
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30),
            ),
            take(rule, 4),
        )
    }

    @Test
    fun monthly_positional_secondMonday() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY, monthlyMode = MonthlyMode.DAY_OF_WEEK_POSITION,
            weekOfMonth = 2, positionalDay = DayOfWeek.MONDAY,
            anchorDate = LocalDate.of(2026, 1, 1),
        )
        val dates = take(rule, 3)
        // 2nd Monday of Jan 2026 = Jan 12; Feb = Feb 9; Mar = Mar 9.
        assertEquals(
            listOf(LocalDate.of(2026, 1, 12), LocalDate.of(2026, 2, 9), LocalDate.of(2026, 3, 9)),
            dates,
        )
    }

    @Test
    fun monthly_positional_lastFriday() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY, monthlyMode = MonthlyMode.DAY_OF_WEEK_POSITION,
            weekOfMonth = -1, positionalDay = DayOfWeek.FRIDAY,
            anchorDate = LocalDate.of(2026, 1, 1),
        )
        val dates = take(rule, 2)
        assertTrue(dates.all { it.dayOfWeek == DayOfWeek.FRIDAY })
        assertEquals(LocalDate.of(2026, 1, 30), dates[0]) // last Friday of Jan 2026
    }

    @Test
    fun yearly_sameDate() {
        val rule = RecurrenceRule(Frequency.YEARLY, anchorDate = LocalDate.of(2026, 11, 18))
        assertEquals(
            listOf(LocalDate.of(2026, 11, 18), LocalDate.of(2027, 11, 18), LocalDate.of(2028, 11, 18)),
            take(rule, 3),
        )
    }

    @Test
    fun yearly_feb29_onlyLeapYears() {
        // Feb 29, 2024 → next valid Feb 29 is 2028 (2025-27 are common years).
        val rule = RecurrenceRule(
            Frequency.YEARLY, anchorDate = LocalDate.of(2024, 2, 29),
            missingDayPolicy = MissingDayPolicy.SKIP,
        )
        assertEquals(
            listOf(LocalDate.of(2024, 2, 29), LocalDate.of(2028, 2, 29), LocalDate.of(2032, 2, 29)),
            take(rule, 3),
        )
    }

    @Test
    fun yearly_feb29_lastDayPolicy_clampsToFeb28() {
        val rule = RecurrenceRule(
            Frequency.YEARLY, anchorDate = LocalDate.of(2024, 2, 29),
            missingDayPolicy = MissingDayPolicy.LAST_DAY,
        )
        assertEquals(
            listOf(
                LocalDate.of(2024, 2, 29), LocalDate.of(2025, 2, 28),
                LocalDate.of(2026, 2, 28), LocalDate.of(2027, 2, 28), LocalDate.of(2028, 2, 29),
            ),
            take(rule, 5),
        )
    }

    @Test
    fun endMode_count_stopsAfterN() {
        val rule = RecurrenceRule(
            Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1),
            endMode = EndMode.COUNT, count = 3,
        )
        assertEquals(3, RecurrenceEngine.occurrences(rule).count())
    }

    @Test
    fun endMode_untilDate_isInclusive() {
        val rule = RecurrenceRule(
            Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1),
            endMode = EndMode.UNTIL_DATE, until = LocalDate.of(2026, 1, 3),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3)),
            RecurrenceEngine.occurrences(rule).toList(),
        )
    }

    @Test
    fun occurrencesInRange_returnsOnlyWindow() {
        val rule = RecurrenceRule(Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1))
        val window = RecurrenceEngine.occurrencesInRange(
            rule, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 12),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 12)),
            window,
        )
    }

    @Test
    fun nextOccurrence_findsOnOrAfter() {
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY),
            anchorDate = LocalDate.of(2026, 1, 5),
        )
        val next = RecurrenceEngine.nextOccurrence(rule, LocalDate.of(2026, 1, 6))
        assertEquals(LocalDate.of(2026, 1, 12), next)
    }

    @Test
    fun nextOccurrence_nullAfterFiniteSeriesEnds() {
        val rule = RecurrenceRule(
            Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1),
            endMode = EndMode.COUNT, count = 2,
        )
        assertNull(RecurrenceEngine.nextOccurrence(rule, LocalDate.of(2026, 2, 1)))
    }

    @Test
    fun hourly_nextTrigger_landsOnNextStep() {
        val rule = RecurrenceRule(Frequency.HOURLY, interval = 2, anchorDate = LocalDate.of(2026, 1, 1))
        val next = RecurrenceEngine.nextHourlyTrigger(
            rule, LocalTime.of(8, 0), LocalDateTime.of(2026, 1, 1, 9, 0),
        )
        assertEquals(LocalDateTime.of(2026, 1, 1, 10, 0), next)
    }

    @Test
    fun hourly_beforeAnchor_returnsAnchorDateTime() {
        val rule = RecurrenceRule(Frequency.HOURLY, interval = 1, anchorDate = LocalDate.of(2026, 1, 2))
        val next = RecurrenceEngine.nextHourlyTrigger(
            rule, LocalTime.of(7, 0), LocalDateTime.of(2026, 1, 1, 0, 0),
        )
        assertEquals(LocalDateTime.of(2026, 1, 2, 7, 0), next)
    }

    @Test
    fun hourly_showsOncePerDayForDisplay() {
        val rule = RecurrenceRule(Frequency.HOURLY, interval = 3, anchorDate = LocalDate.of(2026, 1, 1))
        val dates = RecurrenceEngine.occurrencesInRange(rule, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 3)), dates)
    }

    @Test
    fun infiniteSeries_isBoundedByRangeQuery() {
        // A NEVER-ending daily rule must still return a finite windowed result.
        val rule = RecurrenceRule(Frequency.DAILY, anchorDate = LocalDate.of(2000, 1, 1))
        val count = RecurrenceEngine.occurrencesInRange(
            rule, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
        ).size
        assertEquals(31, count)
    }
}
