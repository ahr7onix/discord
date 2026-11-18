package com.chronos.app.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.chronos.app.ui.components.CategoryDot
import com.chronos.app.ui.components.EmptyState

@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maxCount = state.byCategory.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Estatísticas", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatsPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = state.period == p,
                        onClick = { viewModel.setPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, StatsPeriod.entries.size),
                    ) { Text(p.label) }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("${state.completedTotal}", style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Concluídos no período", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item {
            Text("Por categoria", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.byCategory.isEmpty()) {
            item { EmptyState("Nada concluído ainda", "Conclua eventos para ver o histórico aqui.") }
        } else {
            items(state.byCategory) { stat ->
                CategoryBar(
                    name = stat.category?.name ?: "Sem categoria",
                    color = stat.category?.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                    count = stat.count,
                    fraction = stat.count.toFloat() / maxCount,
                )
            }
        }
    }
}

@Composable
private fun CategoryBar(name: String, color: Color, count: Int, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryDot(color, size = 10)
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(color)
            )
        }
    }
}
