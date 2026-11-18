package com.chronos.app.recurrence

import com.chronos.app.domain.model.Event
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.model.RecurrenceRule
import com.chronos.app.domain.model.SeriesState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EventModelTest {

    private fun dailyEvent(state: SeriesState = SeriesState.ACTIVE, pauseStart: LocalDate? = null, pauseEnd: LocalDate? = null) =
        Event(
            id = 1, title = "Remédio",
            startTime = LocalTime.of(8, 0),
            rule = RecurrenceRule(Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1)),
            state = state, pauseStart = pauseStart, pauseEnd = pauseEnd,
        )

    @Test
    fun activeSeries_producesEveryDay() {
        val dates = dailyEvent().occurrenceDatesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5))
        assertEquals(5, dates.size)
    }

    @Test
    fun cancelledSeries_producesNothing() {
        val dates = dailyEvent(SeriesState.CANCELLED).occurrenceDatesBetween(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5),
        )
        assertTrue(dates.isEmpty())
    }

    @Test
    fun pauseWindow_removesOnlyPausedDates() {
        val event = dailyEvent(
            state = SeriesState.PAUSED,
            pauseStart = LocalDate.of(2026, 1, 3),
            pauseEnd = LocalDate.of(2026, 1, 4),
        )
        val dates = event.occurrenceDatesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 5)),
            dates,
        )
    }

    @Test
    fun openEndedPause_removesFromStartOnward() {
        val event = dailyEvent(state = SeriesState.PAUSED, pauseStart = LocalDate.of(2026, 1, 3))
        val dates = event.occurrenceDatesBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5))
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)), dates)
    }

    @Test
    fun endTimeBeforeStart_spillsPastMidnight() {
        val event = Event(
            id = 2, title = "Plantão",
            startTime = LocalTime.of(23, 0),
            endTime = LocalTime.of(2, 0),
            rule = RecurrenceRule.single(LocalDate.of(2026, 1, 1)),
        )
        val end = event.endAt(LocalDate.of(2026, 1, 1))
        assertEquals(LocalDate.of(2026, 1, 2).atTime(2, 0), end)
    }

    @Test
    fun endTimeAfterStart_sameDay() {
        val event = Event(
            id = 3, title = "Reunião",
            startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30),
            rule = RecurrenceRule.single(LocalDate.of(2026, 1, 1)),
        )
        assertFalse(event.endAt(LocalDate.of(2026, 1, 1))!!.toLocalDate().isAfter(LocalDate.of(2026, 1, 1)))
    }
}
