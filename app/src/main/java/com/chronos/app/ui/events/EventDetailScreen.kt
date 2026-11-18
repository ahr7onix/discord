package com.chronos.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.domain.model.OccurrenceStatus
import com.chronos.app.domain.model.Priority
import com.chronos.app.domain.model.SeriesState
import com.chronos.app.domain.recurrence.DateTimeFmt
import com.chronos.app.domain.recurrence.RecurrenceDescriber
import com.chronos.app.ui.components.CategoryDot
import com.chronos.app.ui.components.OccurrenceCard
import com.chronos.app.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    viewModel: EventDetailViewModel,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val event = state.event

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(event?.title ?: "Detalhes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (event != null) {
                        IconButton(onClick = { onEdit(event.id) }) { Icon(Icons.Filled.Edit, "Editar") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Excluir") }
                    }
                },
            )
        },
    ) { padding ->
        if (event == null) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!state.loading) Text("Agendamento não encontrado", Modifier.padding(32.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoRow("Horário", event.startTime.format(DateTimeFmt.time24) +
                            (event.endTime?.let { " – " + it.format(DateTimeFmt.time24) } ?: ""))
                        InfoRow("Repetição", RecurrenceDescriber.describe(event.rule))
                        state.category?.let {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Categoria", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f))
                                CategoryDot(Color(it.colorArgb), size = 10)
                                Text(it.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        InfoRow("Prioridade", priorityText(event.priority))
                        InfoRow("Lembretes", event.reminders.joinToString(", ") { leadText(it.minutesBefore) })
                        if (event.state != SeriesState.ACTIVE) {
                            InfoRow("Estado", if (event.state == SeriesState.PAUSED) "Pausado" else "Cancelado")
                        }
                        event.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (event.isRecurring && event.state != SeriesState.CANCELLED) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.pauseResume(event) }, modifier = Modifier.weight(1f)) {
                            Text(if (event.state == SeriesState.PAUSED) "Retomar" else "Pausar")
                        }
                        OutlinedButton(onClick = { viewModel.cancel(event) }, modifier = Modifier.weight(1f)) {
                            Text("Cancelar série")
                        }
                    }
                }
            }

            item { SectionTitle("Próximas ocorrências") }
            if (state.upcoming.isEmpty()) {
                item {
                    Text("Nenhuma ocorrência futura.", Modifier.padding(4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(state.upcoming, key = { it.date.toString() }) { occ ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            occ.date.format(DateTimeFmt.dayMonth).replaceFirstChar { c -> c.uppercase() } +
                                " · " + occ.date.format(DateTimeFmt.weekdayLong),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OccurrenceCard(
                            occurrence = occ,
                            categoryColor = state.category?.colorArgb?.let { Color(it) },
                            onClick = { },
                            onToggleComplete = {
                                if (occ.status == OccurrenceStatus.COMPLETED) viewModel.undoComplete(occ)
                                else viewModel.complete(occ)
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir agendamento?") },
            text = { Text("Esta ação remove o evento e todo o seu histórico. Não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(onBack) }) {
                    Text("Excluir")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(2f))
    }
}

private fun priorityText(p: Priority) = when (p) {
    Priority.LOW -> "Baixa"; Priority.NORMAL -> "Normal"; Priority.HIGH -> "Alta"
}
private fun leadText(min: Int) = when (min) {
    0 -> "No horário"; 60 -> "1 hora"; 1440 -> "1 dia"; else -> "$min min"
}
