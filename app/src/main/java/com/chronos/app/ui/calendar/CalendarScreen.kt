package com.chronos.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.domain.model.EventOccurrence
import com.chronos.app.domain.recurrence.DateTimeFmt
import com.chronos.app.ui.components.EmptyState
import com.chronos.app.ui.components.OccurrenceCard
import com.chronos.app.ui.components.SectionTitle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private val WEEK_HEADERS = listOf("D", "S", "T", "Q", "Q", "S", "S") // Sun..Sat

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenEvent: (Long) -> Unit,
    onNewOnDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDate.collectAsStateWithLifecycle()
    val month by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val byDate by viewModel.occurrencesByDate.collectAsStateWithLifecycle()
    val dayOccurrences by viewModel.selectedDayOccurrences.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = month.format(DateTimeFmt.monthYear).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::previousMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior")
                }
                IconButton(onClick = viewModel::nextMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês")
                }
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CalendarMode.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { viewModel.setMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(index, CalendarMode.entries.size),
                    ) { Text(modeLabel(m)) }
                }
            }
        }

        when (mode) {
            CalendarMode.MONTH -> item {
                MonthGrid(month, selected, byDate, viewModel::selectDate)
            }
            CalendarMode.WEEK -> item {
                WeekRow(selected, byDate, viewModel::selectDate)
            }
            CalendarMode.DAY -> Unit
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    selected.format(DateTimeFmt.dayMonth).replaceFirstChar { it.uppercase() } +
                        " · " + selected.format(DateTimeFmt.weekdayLong),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (dayOccurrences.isEmpty()) {
            item { EmptyState("Sem eventos neste dia", "Toque e segure um dia ou use + para adicionar.") }
        } else {
            items(dayOccurrences, key = { it.event.id to it.date }) { occ ->
                OccurrenceCard(
                    occurrence = occ,
                    categoryColor = occ.event.colorArgb?.let { Color(it) },
                    onClick = { onOpenEvent(occ.event.id) },
                    onToggleComplete = {
                        if (occ.status.name == "COMPLETED") viewModel.undoComplete(occ) else viewModel.complete(occ)
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: java.time.YearMonth,
    selected: LocalDate,
    byDate: Map<LocalDate, List<EventOccurrence>>,
    onSelect: (LocalDate) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            WEEK_HEADERS.forEach { h ->
                Text(h, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val first = month.atDay(1)
        // Align to Sunday-start grid.
        val gridStart = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val weeks = 6
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val date = gridStart.plusDays((w * 7 + d).toLong())
                    DayCell(
                        date = date,
                        inMonth = date.month == month.month,
                        selected = date == selected,
                        hasEvents = byDate[date]?.isNotEmpty() == true,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekRow(
    selected: LocalDate,
    byDate: Map<LocalDate, List<EventOccurrence>>,
    onSelect: (LocalDate) -> Unit,
) {
    val start = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    Row(Modifier.fillMaxWidth()) {
        for (d in 0 until 7) {
            val date = start.plusDays(d.toLong())
            DayCell(
                date = date,
                inMonth = true,
                selected = date == selected,
                hasEvents = byDate[date]?.isNotEmpty() == true,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(date) },
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    hasEvents: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isToday = date == LocalDate.now()
    Box(
        modifier = modifier.aspectRatio(1f).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        !inMonth -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (hasEvents && !selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            )
        }
    }
}

private fun modeLabel(mode: CalendarMode): String = when (mode) {
    CalendarMode.DAY -> "Dia"
    CalendarMode.WEEK -> "Semana"
    CalendarMode.MONTH -> "Mês"
}
