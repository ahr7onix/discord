package com.chronos.app.ui.settings

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.data.repository.ThemeMode
import com.chronos.app.ui.components.CategoryDot
import java.time.DayOfWeek

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(event) {
        if (event is SettingsEvent.Message) {
            android.widget.Toast.makeText(context, (event as SettingsEvent.Message).text,
                android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeEvent()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.let(viewModel::exportBackup) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.let(viewModel::importBackup) }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Ajustes", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        }

        item {
            SettingsSection("Aparência") {
                Text("Tema", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(themeLabel(mode)) },
                        )
                    }
                }
                SwitchLine("Cores dinâmicas (Material You)", settings.dynamicColor, viewModel::setDynamicColor)
            }
        }

        item {
            SettingsSection("Agenda") {
                SwitchLineLabeled("Formato 24 horas", settings.use24hClock, viewModel::setClock24h)
                Text("Primeiro dia da semana", style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY).forEach { day ->
                        FilterChip(
                            selected = settings.firstDayOfWeek == day,
                            onClick = { viewModel.setFirstDayOfWeek(day) },
                            label = { Text(if (day == DayOfWeek.SUNDAY) "Domingo" else "Segunda") },
                        )
                    }
                }
                Text("Antecedência padrão: ${settings.defaultLeadMinutes} min",
                    style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 15, 30, 60).forEach { m ->
                        FilterChip(
                            selected = settings.defaultLeadMinutes == m,
                            onClick = { viewModel.setDefaultLead(m) },
                            label = { Text(if (m == 0) "No horário" else "$m min") },
                        )
                    }
                }
            }
        }

        item {
            SettingsSection("Notificações") {
                SwitchLineLabeled("Som", settings.soundEnabled, viewModel::setSound)
                SwitchLineLabeled("Vibração", settings.vibrationEnabled, viewModel::setVibration)
                if (!viewModel.canScheduleExact()) {
                    Text(
                        "Alarmes exatos não estão permitidos. Os lembretes podem atrasar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    }) { Text("Permitir alarmes exatos") }
                }
            }
        }

        item {
            SettingsSection("Categorias") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text(cat.name) },
                            avatar = { CategoryDot(Color(cat.colorArgb), size = 10) },
                            trailingIcon = {
                                if (!cat.isDefault) {
                                    Icon(
                                        Icons.Filled.Close, "Excluir",
                                        modifier = Modifier
                                            .padding(2.dp)
                                            .clickableNoRipple { viewModel.deleteCategory(cat.id) },
                                    )
                                }
                            },
                        )
                    }
                    AssistChip(
                        onClick = { viewModel.addCategory("Nova", 0xFF8B5CF6.toInt()) },
                        label = { Text("Adicionar") },
                        leadingIcon = { Icon(Icons.Filled.Add, null) },
                    )
                }
            }
        }

        item {
            SettingsSection("Backup local") {
                Text("Exporte e restaure seus dados em um arquivo no aparelho. Nada é enviado para servidores.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("chronos-backup.json") },
                        modifier = Modifier.weight(1f)) { Text("Exportar") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)) { Text("Restaurar") }
                }
            }
        }

        item {
            Text("Chronos 1.0 · dados somente no aparelho · funciona offline",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SwitchLineLabeled(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    SwitchLine(label, checked, onChange)

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Minimal clickable without ripple, for chip trailing icons. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> "Sistema"; ThemeMode.DARK -> "Escuro"; ThemeMode.LIGHT -> "Claro"
}
