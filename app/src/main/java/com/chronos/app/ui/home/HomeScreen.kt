package com.chronos.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.domain.recurrence.DateTimeFmt
import com.chronos.app.ui.components.EmptyState
import com.chronos.app.ui.components.OccurrenceCard
import com.chronos.app.ui.components.SectionTitle
import com.chronos.app.ui.components.StatPill
import com.chronos.app.ui.theme.Overdue
import com.chronos.app.ui.theme.Success
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import java.time.LocalTime

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenEvent: (Long) -> Unit,
    onNewEvent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(greeting(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    state.today.format(DateTimeFmt.dayMonth).replaceFirstChar { it.uppercase() } +
                        " · " + state.today.format(DateTimeFmt.weekdayLong),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatPill("${state.pendingCount}", "Pendentes", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatPill("${state.overdueCount}", "Atrasados", Overdue, Modifier.weight(1f))
                StatPill("${state.completedTodayCount}", "Concluídos", Success, Modifier.weight(1f))
            }
        }

        item { SectionTitle("Hoje") }
        if (state.todayOccurrences.isEmpty()) {
            item { EmptyState("Nada para hoje", "Toque em + para criar um agendamento.") }
        } else {
            items(state.todayOccurrences, key = { it.event.id to it.date }) { occ ->
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

        if (state.upcoming.isNotEmpty()) {
            item { SectionTitle("Próximos") }
            items(state.upcoming, key = { "u" + it.event.id + it.date }) { occ ->
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

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}
