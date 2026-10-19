package com.gymtracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gymtracker.R

/**
 * Potwierdzenie startu nowego treningu, gdy inny wciąż trwa.
 *
 * Repozytorium pilnuje zasady „jeden trening naraz”: start nowego **przerywa**
 * poprzedni, a przerwanego nie da się odzyskać. Skoro tak, to nie robimy tego
 * po cichu — pytamy, a [activeName] mówi, co dokładnie zostanie przerwane.
 */
@Composable
fun StartAnotherWorkoutDialog(
    activeName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_confirm_new_title)) },
        text = { Text(stringResource(R.string.calendar_confirm_new_message, activeName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.calendar_confirm_new_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
