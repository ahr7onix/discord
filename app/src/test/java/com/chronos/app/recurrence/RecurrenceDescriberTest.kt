package com.chronos.app.recurrence

import com.chronos.app.domain.model.EndMode
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.model.MonthlyMode
import com.chronos.app.domain.model.RecurrenceRule
import com.chronos.app.domain.recurrence.RecurrenceDescriber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceDescriberTest {

    @Test
    fun daily() {
        val text = RecurrenceDescriber.describe(
            RecurrenceRule(Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1))
        )
        assertTrue(text.startsWith("Todos os dias"))
    }

    @Test
    fun weeklySpecificDays() {
        val text = RecurrenceDescriber.describe(
            RecurrenceRule(
                frequency = Frequency.WEEKLY,
                daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                anchorDate = LocalDate.of(2026, 1, 5),
            )
        )
        assertTrue(text.contains("Toda semana"))
    }

    @Test
    fun monthlyDay31() {
        val text = RecurrenceDescriber.describe(
            RecurrenceRule(
                frequency = Frequency.MONTHLY, monthlyMode = MonthlyMode.DAY_OF_MONTH,
                dayOfMonth = 31, anchorDate = LocalDate.of(2026, 1, 31),
            )
        )
        assertTrue(text.contains("no dia 31"))
    }

    @Test
    fun infiniteSuffix() {
        val text = RecurrenceDescriber.describe(
            RecurrenceRule(Frequency.DAILY, anchorDate = LocalDate.of(2026, 1, 1), endMode = EndMode.NEVER)
        )
        assertTrue(text.contains("sem data final"))
    }

    @Test
    fun singleNoRepetition() {
        assertEquals("Sem repetição", RecurrenceDescriber.describe(RecurrenceRule.single(LocalDate.of(2026, 1, 1))))
    }
}
