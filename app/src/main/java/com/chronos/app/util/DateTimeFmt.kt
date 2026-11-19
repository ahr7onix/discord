package com.chronos.app.domain.recurrence

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared pt-BR date/time formatters. */
object DateTimeFmt {
    private val PT = Locale("pt", "BR")
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT)
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("dd 'de' MMMM", PT)
    val weekdayLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", PT)
    val time24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", PT)
    val time12: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", PT)
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT)
}
