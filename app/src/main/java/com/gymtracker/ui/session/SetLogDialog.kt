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
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.SetLog
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.model.isCompleteFor
import com.gymtracker.ui.components.toDecimalOrNull
import com.gymtracker.ui.components.toPlainStringOrNull

/**
 * Formularz serii: ciężar i wartość (powtórzenia, czas albo dystans).
 *
 * Pola startują z **poprzedniej serii** tego ćwiczenia albo z celu z planu -
 * w praktyce powtarza się ten sam ciężar, więc wpisywanie go od zera byłoby
 * karą za korzystanie z aplikacji. Pusty ciężar jest w porządku (pompki,
 * plank): znaczeniem serii jest wartość, nie obciążenie.
 *
 * Ten sam formularz obsługuje poprawianie ([existing]) i kasowanie serii.
 */
@Composable
fun SetLogDialog(
    exerciseName: String,
    unit: RepsUnit,
    /** Seria poprawiana albo `null`, gdy dodajemy nową. */
    existing: SetLog?,
    /** Podpowiedź dla nowej serii (poprzednia seria albo cel z planu). */
    suggestion: SetLogSuggestion,
    onDismiss: () -> Unit,
    onConfirm: (SetLogDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var weightText by remember(existing?.id) {
        mutableStateOf((existing?.weightKg ?: suggestion.weightKg).toPlainStringOrNull().orEmpty())
    }
    var valueText by remember(existing?.id) {
        mutableStateOf(
            (existing?.let { if (unit == RepsUnit.SECONDS) it.durationS else it.reps }
                ?: suggestion.value)?.toString().orEmpty(),
        )
    }
    var isWarmup by remember(existing?.id) { mutableStateOf(existing?.isWarmup ?: false) }

    val draft = SetLogDraft(
        weightKg = weightText.toDecimalOrNull(),
        reps = if (unit == RepsUnit.SECONDS) null else valueText.trim().toIntOrNull(),
        durationS = if (unit == RepsUnit.SECONDS) valueText.trim().toIntOrNull() else null,
        isWarmup = isWarmup,
    )
    // Pusty ciężar jest w porządku, ale wartość serii musi być - inaczej
    // zapisalibyśmy wiersz, który nic nie znaczy.
    val isValueValid = draft.isCompleteFor(unit)

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
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { text -> valueText = text },
                    label = { Text(text = stringResource(setValueLabelRes(unit))) },
                    singleLine = true,
                    isError = valueText.isNotEmpty() && !isValueValid,
                    supportingText = {
                        if (valueText.isNotEmpty() && !isValueValid) {
                            Text(text = stringResource(R.string.session_set_required))
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
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
 * Z czego startuje nowa seria: poprzednia zalogowana seria tego ćwiczenia
 * (najczęstszy przypadek) albo cel z planu (powtórzenia, bez ciężaru).
 */
data class SetLogSuggestion(
    val weightKg: Double? = null,
    val value: Int? = null,
) {
    companion object {
        fun of(previous: SetLog?, unit: RepsUnit, targetReps: Int?): SetLogSuggestion {
            val value = previous?.let { if (unit == RepsUnit.SECONDS) it.durationS else it.reps }
            return SetLogSuggestion(
                weightKg = previous?.weightKg,
                value = value ?: targetReps,
            )
        }
    }
}
