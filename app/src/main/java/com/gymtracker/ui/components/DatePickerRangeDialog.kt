package com.gymtracker.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gymtracker.R
import com.gymtracker.ui.calendar.toUtcMillis
import com.gymtracker.ui.calendar.utcMillisToDate
import java.time.LocalDate

/**
 * Wybór jednego dnia z zakresu (Materialowy `DatePicker` w dialogu).
 *
 * Wspólny dla przenoszenia wpisu w kalendarzu i poprawki dnia treningu: oba
 * pytają o jeden dzień w oknie, w którym kalendarz planuje, więc różnić się
 * mają tylko granicami.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerRangeDialog(
    initialDate: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcMillis(),
        selectableDates = DateRangeSelectableDates(minDate = minDate, maxDate = maxDate),
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

/** Zakres dat dla `DatePicker` - material3 pyta o każdy dzień i każdy rok. */
@OptIn(ExperimentalMaterial3Api::class)
class DateRangeSelectableDates(
    private val minDate: LocalDate,
    private val maxDate: LocalDate,
) : SelectableDates {

    private val minMillis = minDate.toUtcMillis()
    private val maxMillis = maxDate.toUtcMillis()

    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis in minMillis..maxMillis

    override fun isSelectableYear(year: Int): Boolean = year in minDate.year..maxDate.year
}
