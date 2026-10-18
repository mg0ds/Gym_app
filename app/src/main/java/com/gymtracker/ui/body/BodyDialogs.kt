package com.gymtracker.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymtracker.R
import com.gymtracker.domain.model.BodyEntry
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.model.MetricType
import com.gymtracker.ui.calendar.DateRangeSelectableDates
import com.gymtracker.ui.calendar.isoText
import com.gymtracker.ui.calendar.toUtcMillis
import com.gymtracker.ui.calendar.utcMillisToDate
import com.gymtracker.ui.components.toDecimalOrNull
import java.time.LocalDate

/**
 * Formularze karty postaci: wskaźnik i pomiar.
 *
 * Jedna decyzja powtarza się w obu: **pole puste znaczy „nie zmieniam”**.
 * Wbudowanemu wskaźnikowi nie trzeba przepisywać nazwy z zasobów, żeby zmienić
 * sam cel, a pomiarowi bez notatki nie trzeba wstawiać pustego tekstu.
 */

/** Wspólny wybór kierunku zmian - „w górę dobrze”, „w dół dobrze”, „bez oceny”. */
@Composable
private fun DirectionRows(
    selected: MetricDirection,
    onSelect: (MetricDirection) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.metric_type_direction),
            style = MaterialTheme.typography.bodyLarge,
        )
        MetricDirection.entries.forEach { direction ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = direction == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(direction) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = direction == selected, onClick = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(direction.labelRes()))
            }
        }
    }
}

@Composable
internal fun MetricTypeDialog(
    initial: MetricType?,
    onDismiss: () -> Unit,
    onSave: (name: String?, unit: String?, direction: MetricDirection, target: Double?) -> Unit,
    onArchiveChange: (MetricType, Boolean) -> Unit,
) {
    // Wbudowany wskaźnik startuje z pustym polem nazwy: jego nazwa jest
    // tłumaczona z zasobów, a puste pole znaczy „zostaw, jak jest”.
    var name by remember { mutableStateOf(if (initial?.builtin != null) "" else initial?.name.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unit.orEmpty()) }
    var target by remember { mutableStateOf(initial?.target?.let { value -> formatNumber(value) }.orEmpty()) }
    var direction by remember { mutableStateOf(initial?.direction ?: MetricDirection.NEUTRAL) }

    val parsedTarget = target.toDecimalOrNull()
    val targetValid = target.isBlank() || parsedTarget != null
    val nameMissing = initial == null && name.isBlank()
    val canSave = !nameMissing && targetValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initial == null) R.string.metric_type_dialog_new
                    else R.string.metric_type_dialog_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value },
                    label = { Text(text = stringResource(R.string.metric_type_name)) },
                    placeholder = initial?.let { type -> { Text(text = type.displayName()) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { value -> unit = value },
                    label = { Text(text = stringResource(R.string.metric_type_unit)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DirectionRows(selected = direction, onSelect = { value -> direction = value })
                OutlinedTextField(
                    value = target,
                    onValueChange = { value -> target = value.filter { char -> char.isDigit() || char == '.' || char == ',' || char == '-' } },
                    label = { Text(text = stringResource(R.string.metric_type_target)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = !targetValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial != null) {
                    TextButton(onClick = { onArchiveChange(initial, !initial.isArchived) }) {
                        Text(
                            text = stringResource(
                                if (initial.isArchived) R.string.metric_type_restore
                                else R.string.metric_type_archive,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name.takeIf { text -> text.isNotBlank() }, unit, direction, parsedTarget)
                },
                enabled = canSave,
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** „Nowy pomiar” albo poprawka istniejącego, z datą, wartością i notatką. */
@Composable
internal fun BodyEntryDialog(
    initial: BodyEntry?,
    unit: String?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (entryId: Long?, date: LocalDate, value: Double, note: String?) -> Unit,
    onDelete: (BodyEntry) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.let { entry -> formatNumber(entry.value) }.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var date by remember { mutableStateOf(initial?.date ?: today) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val value = text.toDecimalOrNull()
    val canSave = value != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initial == null) R.string.metric_entry_dialog_new
                    else R.string.metric_entry_dialog_edit,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value },
                    label = { Text(text = stringResource(R.string.metric_entry_value)) },
                    suffix = unit?.let { measureUnit -> { Text(text = measureUnit) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = false, role = Role.Button, onClick = { pickDate = true })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.metric_entry_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = date.isoText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { value -> note = value },
                    label = { Text(text = stringResource(R.string.metric_entry_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (initial != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(
                            text = stringResource(R.string.metric_entry_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial?.id, date, requireNotNull(value), note) },
                enabled = canSave,
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )

    if (pickDate) {
        PickDateDialog(
            initialDate = date,
            maxDate = today,
            onDismiss = { pickDate = false },
            onConfirm = { picked ->
                date = picked
                pickDate = false
            },
        )
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(text = stringResource(R.string.metric_entry_delete_confirm_title)) },
            text = {
                Text(text = stringResource(R.string.metric_entry_delete_confirm_text, date.isoText()))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(initial)
                    },
                ) {
                    Text(text = stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Wybór dnia pomiaru. Przyszłości nie da się wybrać: pomiar opisuje stan ciała,
 * a nie plan - „jutro 80 kg” nie jest pomiarem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDateDialog(
    initialDate: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcMillis(),
        selectableDates = DateRangeSelectableDates(
            minDate = MAX_YEARS_BACK.let { years -> maxDate.minusYears(years) },
            maxDate = maxDate,
        ),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val selected = state.selectedDateMillis
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { millis -> onConfirm(utcMillisToDate(millis)) } },
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/** Ile lat wstecz można wybrać datę pomiaru - dalej to już nie historia, a zabawa kalendarzem. */
private const val MAX_YEARS_BACK = 10L
