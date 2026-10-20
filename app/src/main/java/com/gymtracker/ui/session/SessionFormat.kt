package com.gymtracker.ui.session

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gymtracker.R
import com.gymtracker.domain.model.ExerciseTracking
import com.gymtracker.domain.model.LastPerformance
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.SetLog
import com.gymtracker.ui.calendar.isoText
import com.gymtracker.ui.components.toPlainStringOrNull

/**
 * Teksty treningu w jednym miejscu: podsumowanie zalogowanej serii i etykieta
 * pola, w którym wpisuje się jej wartość.
 *
 * O tym, **co** jest wartością serii, decyduje typ ćwiczenia
 * (`ExerciseTracking`): powtórzenia, czas, dystans albo czas z powtórzeniami.
 * Jedno miejsce na tę decyzję = etykieta pola i podsumowanie listy nie rozjadą się.
 */

/** „60 kg × 5”, „60 kg × 45 s”, „45 s”, „30 m”, „60 s × 10 powt.”. */
@Composable
fun setSummary(log: SetLog, tracking: ExerciseTracking): String {
    val weight = log.weightKg.toPlainStringOrNull()
    val value = setValueText(log, tracking)
    val core = when {
        weight != null && value != null -> stringResource(
            R.string.session_set_summary_weight_value,
            weight,
            value,
        )

        weight != null -> stringResource(R.string.session_set_summary_weight, weight)
        value != null -> value
        else -> ""
    }
    return if (log.isWarmup) {
        stringResource(R.string.session_set_summary_warmup, core)
    } else {
        core
    }
}

/** Wartość serii bez ciężaru: „5 powt.”, „45 s”, „30 m”, „60 s × 10 powt.”. */
@Composable
private fun setValueText(log: SetLog, tracking: ExerciseTracking): String? = when {
    tracking == ExerciseTracking.TIME_REPS -> {
        val duration = log.durationS
        val reps = log.reps
        if (duration != null && reps != null) {
            stringResource(R.string.session_set_summary_time_reps, duration, reps)
        } else {
            duration?.let { seconds -> stringResource(R.string.session_set_summary_time, seconds) }
                ?: reps?.let { value -> stringResource(R.string.session_set_summary_reps, value) }
        }
    }

    tracking.usesDuration -> log.durationS?.let { stringResource(R.string.session_set_summary_time, it) }
    tracking == ExerciseTracking.DISTANCE ->
        log.reps?.let { stringResource(R.string.session_set_summary_distance, it) }

    else -> log.reps?.let { stringResource(R.string.session_set_summary_reps, it) }
}

/** Etykieta pola z wartością serii - „Powtórzenia”, „Czas (s)”, „Dystans (m)”. */
@StringRes
fun setValueLabelRes(unit: RepsUnit): Int = when (unit) {
    RepsUnit.REPS -> R.string.session_set_reps
    RepsUnit.SECONDS -> R.string.session_set_time
    RepsUnit.METERS -> R.string.session_set_distance
}

/**
 * „Ostatnio (12.10.2026): 55–75 kg × 5” - co użytkownik zrobił ostatnio.
 *
 * Przy rampie pokazujemy **zakres**, a nie tylko najcięższą serię: 75 kg bez
 * „55” nie mówi, od czego zacząć. Gdy ciężar jest stały, zakres zwija się do
 * jednej liczby.
 */
@Composable
fun lastPerformanceText(performance: LastPerformance, tracking: ExerciseTracking): String {
    val sets = performance.workingSets
    val value = when {
        tracking.usesDuration -> {
            val duration = sets.mapNotNull { set -> set.durationS }.maxOrNull()
            val time = duration?.let { seconds ->
                stringResource(R.string.session_set_summary_time, seconds)
            }
            if (tracking == ExerciseTracking.TIME_REPS) {
                val reps = sets.mapNotNull { set -> set.reps }.maxOrNull()
                listOfNotNull(time, reps?.let { count ->
                    stringResource(R.string.session_set_summary_reps, count)
                }).joinToString(separator = " × ")
            } else {
                time
            }
        }

        tracking == ExerciseTracking.DISTANCE -> sets.mapNotNull { set -> set.reps }.maxOrNull()
            ?.let { meters -> stringResource(R.string.session_set_summary_distance, meters) }

        else -> {
            val weights = sets.mapNotNull { set -> set.weightKg }
            val reps = sets.mapNotNull { set -> set.reps }.distinct().singleOrNull()
            val weightText = when {
                weights.isEmpty() -> null
                weights.min() < weights.max() -> stringResource(
                    R.string.session_last_weight_range,
                    weights.min().toPlainStringOrNull().orEmpty(),
                    weights.max().toPlainStringOrNull().orEmpty(),
                )

                else -> stringResource(
                    R.string.session_set_summary_weight,
                    weights.max().toPlainStringOrNull().orEmpty(),
                )
            }
            listOfNotNull(
                weightText,
                reps?.let { count -> stringResource(R.string.session_set_summary_reps, count) },
            ).joinToString(separator = " × ")
        }
    } ?: return ""

    return stringResource(
        R.string.session_last_performance,
        performance.date.isoText(),
        value,
    )
}
