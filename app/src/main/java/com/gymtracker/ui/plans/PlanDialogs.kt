package com.gymtracker.ui.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymtracker.R
import com.gymtracker.domain.model.PlanExercise
import com.gymtracker.domain.model.PlanExerciseTargets
import com.gymtracker.domain.model.RepsUnit

/**
 * Okna dialogowe planu. Pola liczbowe trzymamy jako tekst - tak je wpisuje
 * użytkownik - i zamieniamy na liczby dopiero przy potwierdzeniu. Dzięki temu
 * puste pole („nie ustawiono”) to nie to samo co 0.
 */

/** Nazwa i opis planu - do tworzenia i do zmiany. */
@Composable
fun PlanFormDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
    initialName: String = "",
    initialDescription: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value },
                    label = { Text(text = stringResource(R.string.plan_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { value -> description = value },
                    label = { Text(text = stringResource(R.string.plan_description_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank(),
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Nazwa dnia planu i (opcjonalnie) dzień tygodnia, w który ma wypadać. */
@Composable
fun PlanDayDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, dayOfWeek: Int?) -> Unit,
    initialName: String = "",
    initialDayOfWeek: Int? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var dayOfWeek by rememberSaveable { mutableStateOf(initialDayOfWeek) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.plan_day_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { value -> name = value },
                    label = { Text(text = stringResource(R.string.plan_day_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(text = stringResource(R.string.plan_day_weekday_label))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    weekdayOptions.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { day ->
                                FilterChip(
                                    selected = dayOfWeek == day,
                                    onClick = { dayOfWeek = if (dayOfWeek == day) null else day },
                                    label = {
                                        Text(text = weekdayLabel(day).orEmpty().take(3))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, dayOfWeek) },
                enabled = name.isNotBlank(),
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

/** Cel ćwiczenia: serie, powtórzenia (albo czas), ciężar, przerwa i komentarz. */
@Composable
fun ExerciseTargetDialog(
    exercise: PlanExercise,
    onDismiss: () -> Unit,
    onConfirm: (PlanExerciseTargets) -> Unit,
) {
    var sets by rememberSaveable { mutableStateOf(exercise.sets?.toString().orEmpty()) }
    var repsMin by rememberSaveable { mutableStateOf(exercise.repsMin?.toString().orEmpty()) }
    var repsMax by rememberSaveable { mutableStateOf(exercise.repsMax?.toString().orEmpty()) }
    var unit by rememberSaveable { mutableStateOf(exercise.repsUnit) }
    var weight by rememberSaveable { mutableStateOf(exercise.weightKg?.toPlainString().orEmpty()) }
    var rest by rememberSaveable { mutableStateOf(exercise.restSeconds?.toString().orEmpty()) }
    var note by rememberSaveable { mutableStateOf(exercise.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = exercise.exerciseName) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NumberField(
                    value = sets,
                    onValueChange = { sets = it },
                    label = stringResource(R.string.plan_sets_label),
                )

                Text(text = stringResource(R.string.plan_reps_unit_label))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepsUnit.entries.forEach { option ->
                        FilterChip(
                            selected = unit == option,
                            onClick = { unit = option },
                            label = { Text(text = repsUnitLabel(option)) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        value = repsMin,
                        onValueChange = { repsMin = it },
                        label = stringResource(R.string.plan_reps_from_label),
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = repsMax,
                        onValueChange = { repsMax = it },
                        label = stringResource(R.string.plan_reps_to_label),
                        modifier = Modifier.weight(1f),
                    )
                }

                NumberField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = stringResource(R.string.plan_weight_label),
                    keyboardType = KeyboardType.Decimal,
                    allowDecimal = true,
                )

                NumberField(
                    value = rest,
                    onValueChange = { rest = it },
                    label = stringResource(R.string.plan_rest_label),
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { value -> note = value },
                    label = { Text(text = stringResource(R.string.plan_note_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        PlanExerciseTargets(
                            sets = sets.toIntOrNull(),
                            repsMin = repsMin.toIntOrNull(),
                            repsMax = repsMax.toIntOrNull(),
                            repsUnit = unit,
                            weightKg = weight.toDoubleOrNull(),
                            restSeconds = rest.toIntOrNull(),
                            note = note.takeIf { text -> text.isNotBlank() },
                        ),
                    )
                },
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

/** Wspólne potwierdzenie usunięcia (plan, dzień, ćwiczenie z dnia). */
@Composable
fun ConfirmDeleteDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.plan_delete_title)) },
        text = { Text(text = text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Number,
    allowDecimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val cleaned = if (allowDecimal) {
                input.filter { char -> char.isDigit() || char == '.' || char == ',' }
            } else {
                input.filter { char -> char.isDigit() }
            }
            onValueChange(cleaned)
        },
        label = { Text(text = label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

@Composable
private fun repsUnitLabel(unit: RepsUnit): String = when (unit) {
    RepsUnit.REPS -> stringResource(R.string.plan_unit_reps)
    RepsUnit.SECONDS -> stringResource(R.string.plan_unit_seconds)
    RepsUnit.METERS -> stringResource(R.string.plan_unit_meters)
}

/** „20.5” bez zbędnych zer końcowych (ciężar w polu tekstowym). */
private fun Double.toPlainString(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()
