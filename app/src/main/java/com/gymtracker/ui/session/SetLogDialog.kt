package com.gymtracker.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gymtracker.R
import com.gymtracker.domain.model.ExerciseTracking
import com.gymtracker.domain.model.LastPerformanceSet
import com.gymtracker.domain.model.SetLog
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.model.isCompleteFor
import com.gymtracker.ui.components.toDecimalOrNull
import com.gymtracker.ui.components.toPlainStringOrNull

/**
 * Formularz serii: tylko te pola, które wynikają z typu ćwiczenia.
 *
 * Sztanga pyta o ciężar i powtórzenia, pompki tylko o powtórzenia, bieżnia
 * tylko o czas, a deska z unoszeniem nóg o czas i powtórzenia. Ciężar jest
 * opcjonalny wszędzie tam, gdzie się pojawia.
 *
 * Pola startują z **poprzedniej serii** tego ćwiczenia, a przy pierwszej serii
 * treningu - z pierwszego zestawu poprzedniego treningu. Dla rampy
 * (55/65/75/75/75) to właśnie 55 kg, a nie 75 z końca.
 *
 * Ten sam formularz obsługuje poprawianie ([existing]) i kasowanie serii.
 */
@Composable
fun SetLogDialog(
    exerciseName: String,
    tracking: ExerciseTracking,
    /** Seria poprawiana albo `null`, gdy dodajemy nową. */
    existing: SetLog?,
    /** Podpowiedź dla nowej serii (poprzednia seria albo poprzedni trening). */
    suggestion: SetLogSuggestion,
    onDismiss: () -> Unit,
    onConfirm: (SetLogDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    // Stary wiersz mógł mieć ciężar, mimo że ćwiczenie go dziś nie używa -
    // pokazujemy go przy poprawianiu, żeby zapis nie skasował danych.
    val showWeight = tracking.usesWeight || existing?.weightKg != null
    var weightText by remember(existing?.id) {
        mutableStateOf((existing?.weightKg ?: suggestion.weightKg).toPlainStringOrNull().orEmpty())
    }
    var mainText by remember(existing?.id) {
        val initial = existing?.let { log ->
            if (tracking.usesDuration) log.durationS else log.reps
        } ?: if (tracking.usesDuration) suggestion.durationS else suggestion.reps
        mutableStateOf(initial?.toString().orEmpty())
    }
    var repsText by remember(existing?.id) {
        mutableStateOf((existing?.reps ?: suggestion.reps)?.toString().orEmpty())
    }
    var isWarmup by remember(existing?.id) { mutableStateOf(existing?.isWarmup ?: false) }

    val mainValue = mainText.trim().toIntOrNull()
    val draft = SetLogDraft(
        weightKg = if (showWeight) weightText.toDecimalOrNull() else null,
        reps = when {
            tracking == ExerciseTracking.TIME_REPS -> repsText.trim().toIntOrNull()
            tracking.usesDuration -> null
            else -> mainValue
        },
        durationS = if (tracking.usesDuration) mainValue else null,
        isWarmup = isWarmup,
    )
    val isValueValid = draft.isCompleteFor(tracking)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = existing?.let { stringResource(R.string.session_set_edit_title, it.setIndex) }
                    ?: stringResource(R.string.session_set_add_title),
            )
        },
        text = {
            Column {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showWeight) {
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { text -> weightText = text },
                        label = { Text(text = stringResource(R.string.session_set_weight)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = mainText,
                    onValueChange = { text -> mainText = text },
                    label = { Text(text = stringResource(setValueLabelRes(tracking.unit))) },
                    singleLine = true,
                    isError = mainText.isNotEmpty() && !isValueValid,
                    supportingText = {
                        if (mainText.isNotEmpty() && !isValueValid) {
                            Text(text = stringResource(R.string.session_set_required))
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = if (tracking == ExerciseTracking.TIME_REPS) ImeAction.Next else ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (showWeight) 0.dp else 12.dp),
                )
                if (tracking == ExerciseTracking.TIME_REPS) {
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { text -> repsText = text },
                        label = { Text(text = stringResource(R.string.session_set_reps)) },
                        singleLine = true,
                        isError = repsText.isNotEmpty() && !isValueValid,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.session_set_warmup),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = isWarmup, onCheckedChange = { checked -> isWarmup = checked })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft) },
                enabled = isValueValid,
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.session_set_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

/**
 * Z czego startuje nowa seria: poprzednia seria w tym treningu, przy pierwszej -
 * pierwsza seria poprzedniego treningu, a gdy historii nie ma - cel z planu.
 */
data class SetLogSuggestion(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationS: Int? = null,
) {
    companion object {
        fun of(
            previous: SetLog?,
            lastPerformance: LastPerformanceSet?,
            tracking: ExerciseTracking,
            targetValue: Int?,
        ): SetLogSuggestion {
            previous?.let { log ->
                return SetLogSuggestion(
                    weightKg = log.weightKg,
                    reps = log.reps,
                    durationS = log.durationS,
                )
            }
            lastPerformance?.let { set ->
                return SetLogSuggestion(
                    weightKg = set.weightKg,
                    reps = set.reps,
                    durationS = set.durationS,
                )
            }
            return when {
                tracking.usesDuration -> SetLogSuggestion(durationS = targetValue)
                else -> SetLogSuggestion(reps = targetValue)
            }
        }
    }
}
