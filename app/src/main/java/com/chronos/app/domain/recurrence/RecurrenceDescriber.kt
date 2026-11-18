package com.chronos.app.domain.recurrence

import com.chronos.app.domain.model.EndMode
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.model.MonthlyMode
import com.chronos.app.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Renders a [RecurrenceRule] as pt-BR text for lists and detail screens. */
object RecurrenceDescriber {

    private val PT = Locale("pt", "BR")

    fun describe(rule: RecurrenceRule): String {
        val base = when (rule.frequency) {
            Frequency.NONE -> "Sem repetição"
            Frequency.DAILY -> daily(rule.interval)
            Frequency.WEEKLY -> weekly(rule)
            Frequency.MONTHLY -> monthly(rule)
            Frequency.YEARLY -> yearly(rule)
        }
        if (rule.frequency == Frequency.NONE) return base
        return base + endSuffix(rule)
    }

    private fun daily(interval: Int) =
        if (interval == 1) "Todos os dias" else "A cada $interval dias"

    private fun weekly(rule: RecurrenceRule): String {
        val days = rule.daysOfWeek.ifEmpty { setOf(rule.anchorDate.dayOfWeek) }
        val every = when {
            rule.interval == 1 -> "Toda semana"
            rule.interval == 2 -> "A cada duas semanas"
            else -> "A cada ${rule.interval} semanas"
        }
        if (days.size == 7) return "$every, todos os dias"
        val names = days.sortedBy { it.value }.joinToString(", ") { weekdayName(it) }
        return "$every, $names"
    }

    private fun monthly(rule: RecurrenceRule): String {
        val cadence = if (rule.interval == 1) "Todo mês" else "A cada ${rule.interval} meses"
        return when (rule.monthlyMode) {
            MonthlyMode.DAY_OF_MONTH -> "$cadence, no dia ${rule.dayOfMonth}"
            MonthlyMode.DAY_OF_WEEK_POSITION -> {
                val pos = positionName(rule.weekOfMonth)
                "$cadence, $pos ${weekdayName(rule.positionalDay)}"
            }
        }
    }

    private fun yearly(rule: RecurrenceRule): String {
        val cadence = if (rule.interval == 1) "Todo ano" else "A cada ${rule.interval} anos"
        val month = rule.anchorDate.month.getDisplayName(TextStyle.FULL, PT)
        return "$cadence, em ${rule.anchorDate.dayOfMonth} de $month"
    }

    private fun endSuffix(rule: RecurrenceRule): String = when (rule.endMode) {
        EndMode.NEVER -> " · sem data final"
        EndMode.UNTIL_DATE -> " · até ${rule.until?.format(DateTimeFmt.shortDate)}"
        EndMode.COUNT -> " · ${rule.count} ocorrências"
    }

    private fun weekdayName(d: DayOfWeek): String =
        d.getDisplayName(TextStyle.FULL, PT).replaceFirstChar { it.titlecase(PT) }

    private fun positionName(week: Int): String = when (week) {
        1 -> "na primeira"
        2 -> "na segunda"
        3 -> "na terceira"
        4 -> "na quarta"
        -1 -> "na última"
        else -> "na ${week}ª"
    }
}
