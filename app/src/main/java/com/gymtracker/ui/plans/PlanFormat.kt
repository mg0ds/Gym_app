package com.gymtracker.ui.plans

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gymtracker.R
import com.gymtracker.domain.model.RepsUnit

/**
 * Teksty planu w jednym miejscu: cel (serie × powtórzenia), przerwa i dni
 * tygodnia. Ekran planu i ekran treningu pokazują ten sam cel, więc format
 * musi być wspólny - inaczej rozjechałby się przy pierwszej zmianie.
 */

/** „5 × 5”, „3 × 12-15”, „3 × 60 s” - jedna linia z celem ćwiczenia. */
@Composable
fun targetText(
    sets: Int?,
    repsMin: Int?,
    repsMax: Int?,
    unit: RepsUnit,
): String {
    val reps = repsRange(repsMin, repsMax)
    return when {
        sets != null && reps != null ->
            stringResource(R.string.plan_target_sets_reps, sets, reps) + unitSuffix(unit)

        sets != null -> pluralStringResource(R.plurals.plan_target_sets, sets, sets)

        reps != null -> reps + unitSuffix(unit)

        else -> stringResource(R.string.plan_target_none)
    }
}

/** „przerwa 90 s” albo `null`, gdy przerwa nie jest ustawiona. */
@Composable
fun restText(restSeconds: Int?): String? =
    restSeconds?.takeIf { it > 0 }?.let { seconds ->
        stringResource(R.string.plan_rest, seconds)
    }

/** „Poniedziałek” … „Niedziela” albo `null`, gdy dzień nie jest przypisany. */
@Composable
fun weekdayLabel(dayOfWeek: Int?): String? =
    dayOfWeek?.let { day -> stringResource(weekdayLabelRes(day)) }

@StringRes
private fun weekdayLabelRes(dayOfWeek: Int): Int = when (dayOfWeek) {
    0 -> R.string.weekday_monday
    1 -> R.string.weekday_tuesday
    2 -> R.string.weekday_wednesday
    3 -> R.string.weekday_thursday
    4 -> R.string.weekday_friday
    5 -> R.string.weekday_saturday
    else -> R.string.weekday_sunday
}

/** Zakres powtórzeń bez jednostki: „5”, „8-12”; `null`, gdy brak danych. */
private fun repsRange(repsMin: Int?, repsMax: Int?): String? = when {
    repsMin != null && repsMax != null && repsMin != repsMax -> "$repsMin-$repsMax"
    repsMin != null -> repsMin.toString()
    repsMax != null -> repsMax.toString()
    else -> null
}

private fun unitSuffix(unit: RepsUnit): String = when (unit) {
    RepsUnit.REPS -> ""
    RepsUnit.SECONDS -> " s"
    RepsUnit.METERS -> " m"
}

/** Wszystkie dni tygodnia w kolejności od poniedziałku - do wyboru w formularzu. */
val weekdayOptions: List<Int> = (0..6).toList()
