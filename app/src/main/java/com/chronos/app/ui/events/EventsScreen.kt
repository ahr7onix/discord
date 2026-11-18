package com.chronos.app.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.domain.model.Event
import com.chronos.app.domain.model.SeriesState
import com.chronos.app.domain.recurrence.RecurrenceDescriber
import com.chronos.app.ui.components.CategoryDot
import com.chronos.app.ui.components.EmptyState

@Composable
fun EventsScreen(
    viewModel: EventsViewModel,
    onOpenEvent: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = { Text("Buscar agendamentos") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
            )
        }

        if (state.events.isEmpty()) {
            item { EmptyState("Nenhum agendamento", "Crie o primeiro tocando em +.") }
        } else {
            items(state.events, key = { it.id }) { event ->
                EventRow(
                    event = event,
                    categoryColor = (event.categoryId?.let { state.categories[it]?.colorArgb }
                        ?: event.colorArgb)?.let { Color(it) },
                    onClick = { onOpenEvent(event.id) },
                    onPause = { viewModel.pause(event) },
                    onResume = { viewModel.resume(event) },
                    onCancel = { viewModel.cancel(event) },
                    onDelete = { viewModel.delete(event) },
                )
            }
        }
    }
}

@Composable
private fun EventRow(
    event: Event,
    categoryColor: Color?,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (categoryColor != null) CategoryDot(categoryColor, size = 12)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(event.startTime.toString())
                        append(" · ")
                        append(RecurrenceDescriber.describe(event.rule))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (event.state != SeriesState.ACTIVE) {
                    Text(
                        if (event.state == SeriesState.PAUSED) "Pausado" else "Cancelado",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, "Opções")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Editar") }, onClick = { menuOpen = false; onClick() })
                if (event.isRecurring) {
                    if (event.state == SeriesState.PAUSED) {
                        DropdownMenuItem(text = { Text("Retomar") }, onClick = { menuOpen = false; onResume() })
                    } else {
                        DropdownMenuItem(text = { Text("Pausar") }, onClick = { menuOpen = false; onPause() })
                    }
                    DropdownMenuItem(text = { Text("Cancelar série") }, onClick = { menuOpen = false; onCancel() })
                }
                DropdownMenuItem(text = { Text("Excluir") }, onClick = { menuOpen = false; onDelete() })
            }
        }
    }
}
