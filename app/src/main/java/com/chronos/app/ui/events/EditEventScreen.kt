package com.chronos.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronos.app.domain.model.EditScope
import com.chronos.app.domain.model.EndMode
import com.chronos.app.domain.model.Frequency
import com.chronos.app.domain.model.MonthlyMode
import com.chronos.app.domain.model.Priority
import com.chronos.app.domain.model.ReminderLead
import com.chronos.app.domain.recurrence.DateTimeFmt
import com.chronos.app.ui.components.CategoryDot
import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val PT = Locale("pt", "BR")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditEventScreen(
    viewModel: EditEventViewModel,
    onDone: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showUntilPicker by remember { mutableStateOf(false) }
    var showScopeDialog by remember { mutableStateOf(false) }

    val attemptSave = {
        if (!form.isNew && form.isRecurring) showScopeDialog = true
        else viewModel.save(EditScope.ALL, onDone)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isNew) "Novo agendamento" else "Editar") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancelar") }
                },
                actions = {
                    IconButton(onClick = attemptSave, enabled = form.titleValid) {
                        Icon(Icons.Filled.Check, "Salvar")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                label = { Text("Título") },
                isError = form.title.isNotEmpty() && !form.titleValid,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.update { it.copy(description = v) } },
                label = { Text("Descrição (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Label("Categoria")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.categoryId == null,
                    onClick = { viewModel.update { it.copy(categoryId = null) } },
                    label = { Text("Nenhuma") },
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = form.categoryId == cat.id,
                        onClick = { viewModel.update { it.copy(categoryId = cat.id) } },
                        label = { Text(cat.name) },
                        leadingIcon = { CategoryDot(Color(cat.colorArgb), size = 10) },
                    )
                }
            }

            Label("Prioridade")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Priority.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = form.priority == p,
                        onClick = { viewModel.update { it.copy(priority = p) } },
                        shape = SegmentedButtonDefaults.itemShape(i, Priority.entries.size),
                    ) { Text(priorityLabel(p)) }
                }
            }

            Label("Data e horário")
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(form.date.format(DateTimeFmt.shortDate) + " · " +
                    form.date.format(DateTimeFmt.weekdayLong).replaceFirstChar { it.uppercase() })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                    Text("Início " + form.startTime.format(DateTimeFmt.time24))
                }
                if (form.hasEndTime) {
                    OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                        Text("Fim " + form.endTime.format(DateTimeFmt.time24))
                    }
                }
            }
            SwitchRow("Definir horário final", form.hasEndTime) { on ->
                viewModel.update { it.copy(hasEndTime = on) }
            }

            Label("Repetição")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Frequency.entries.forEach { freq ->
                    FilterChip(
                        selected = form.frequency == freq,
                        onClick = { viewModel.update { it.copy(frequency = freq) } },
                        label = { Text(frequencyLabel(freq)) },
                    )
                }
            }

            if (form.isRecurring) {
                IntervalStepper(form.frequency, form.interval) { v ->
                    viewModel.update { it.copy(interval = v) }
                }
            }

            if (form.frequency == Frequency.WEEKLY) {
                Label("Dias da semana")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    orderedWeekdays().forEach { day ->
                        FilterChip(
                            selected = day in form.daysOfWeek,
                            onClick = { viewModel.toggleWeekday(day) },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, PT).take(3)) },
                        )
                    }
                }
            }

            if (form.frequency == Frequency.MONTHLY) {
                Label("Modo mensal")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = form.monthlyMode == MonthlyMode.DAY_OF_MONTH,
                        onClick = { viewModel.update { it.copy(monthlyMode = MonthlyMode.DAY_OF_MONTH) } },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Dia ${form.date.dayOfMonth}") }
                    SegmentedButton(
                        selected = form.monthlyMode == MonthlyMode.DAY_OF_WEEK_POSITION,
                        onClick = { viewModel.update { it.copy(monthlyMode = MonthlyMode.DAY_OF_WEEK_POSITION) } },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Posição") }
                }
            }

            Label("Término")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(EndMode.NEVER, EndMode.UNTIL_DATE, EndMode.COUNT)
                modes.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = form.endMode == m,
                        onClick = { viewModel.update { it.copy(endMode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        enabled = form.isRecurring || m == EndMode.NEVER,
                    ) { Text(endLabel(m)) }
                }
            }
            when (form.endMode) {
                EndMode.UNTIL_DATE -> OutlinedButton(
                    onClick = { showUntilPicker = true }, modifier = Modifier.fillMaxWidth()
                ) { Text("Até " + (form.until ?: form.date).format(DateTimeFmt.shortDate)) }
                EndMode.COUNT -> IntervalStepper(null, form.count, suffix = "ocorrências") { v ->
                    viewModel.update { it.copy(count = v) }
                }
                EndMode.NEVER -> Unit
            }

            Label("Lembretes")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reminderOptions().forEach { lead ->
                    FilterChip(
                        selected = form.reminders.any { it.minutesBefore == lead.minutesBefore },
                        onClick = { viewModel.toggleReminder(lead) },
                        label = { Text(reminderLabel(lead)) },
                    )
                }
            }

            Button(
                onClick = attemptSave,
                enabled = form.titleValid && !form.saving,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) { Text(if (form.isNew) "Criar agendamento" else "Salvar alterações") }
        }
    }

    // ---- dialogs ----
    if (showDatePicker) {
        DatePickerModal(initial = form.date, onDismiss = { showDatePicker = false }) { picked ->
            viewModel.update { it.copy(date = picked) }
            showDatePicker = false
        }
    }
    if (showUntilPicker) {
        DatePickerModal(initial = form.until ?: form.date, onDismiss = { showUntilPicker = false }) { picked ->
            viewModel.update { it.copy(until = picked) }
            showUntilPicker = false
        }
    }
    if (showStartPicker) {
        TimePickerModal(initial = form.startTime, onDismiss = { showStartPicker = false }) { picked ->
            viewModel.update { it.copy(startTime = picked) }
            showStartPicker = false
        }
    }
    if (showEndPicker) {
        TimePickerModal(initial = form.endTime, onDismiss = { showEndPicker = false }) { picked ->
            viewModel.update { it.copy(endTime = picked) }
            showEndPicker = false
        }
    }
    if (showScopeDialog) {
        ScopeDialog(
            onDismiss = { showScopeDialog = false },
            onPick = { scope -> showScopeDialog = false; viewModel.save(scope, onDone) },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun IntervalStepper(
    frequency: Frequency?,
    value: Int,
    suffix: String? = null,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("A cada", style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { if (value > 1) onChange(value - 1) }) { Text("−") }
        Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { onChange(value + 1) }) { Text("+") }
        Text(suffix ?: unitLabel(frequency, value), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerModal(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun ScopeDialog(onDismiss: () -> Unit, onPick: (EditScope) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aplicar alteração a") },
        confirmButton = {},
        text = {
            Column {
                TextButton(onClick = { onPick(EditScope.THIS_ONLY) }) { Text("Somente esta ocorrência") }
                TextButton(onClick = { onPick(EditScope.THIS_AND_FUTURE) }) { Text("Esta e as próximas") }
                TextButton(onClick = { onPick(EditScope.ALL) }) { Text("Toda a série") }
            }
        },
    )
}

// ---- labels ----
private fun priorityLabel(p: Priority) = when (p) {
    Priority.LOW -> "Baixa"; Priority.NORMAL -> "Normal"; Priority.HIGH -> "Alta"
}
private fun frequencyLabel(f: Frequency) = when (f) {
    Frequency.NONE -> "Nunca"; Frequency.DAILY -> "Diária"; Frequency.WEEKLY -> "Semanal"
    Frequency.MONTHLY -> "Mensal"; Frequency.YEARLY -> "Anual"
}
private fun endLabel(e: EndMode) = when (e) {
    EndMode.NEVER -> "Nunca"; EndMode.UNTIL_DATE -> "Até data"; EndMode.COUNT -> "Nº vezes"
}
private fun unitLabel(f: Frequency?, n: Int) = when (f) {
    Frequency.DAILY -> if (n == 1) "dia" else "dias"
    Frequency.WEEKLY -> if (n == 1) "semana" else "semanas"
    Frequency.MONTHLY -> if (n == 1) "mês" else "meses"
    Frequency.YEARLY -> if (n == 1) "ano" else "anos"
    else -> ""
}
private fun reminderLabel(l: ReminderLead) = when (l.minutesBefore) {
    0 -> "No horário"; 5 -> "5 min"; 15 -> "15 min"; 30 -> "30 min"; 60 -> "1 hora"; 1440 -> "1 dia"
    else -> "${l.minutesBefore} min"
}
private fun reminderOptions() = listOf(
    ReminderLead.AT_TIME, ReminderLead.MIN_5, ReminderLead.MIN_15,
    ReminderLead.MIN_30, ReminderLead.HOUR_1, ReminderLead.DAY_1,
)
private fun orderedWeekdays(): List<DayOfWeek> = listOf(
    DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
)
