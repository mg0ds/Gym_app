package com.gymtracker.ui.session

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gymtracker.R
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.SetLog
import com.gymtracker.ui.components.toPlainStringOrNull

/**
 * Teksty treningu w jednym miejscu: podsumowanie zalogowanej serii i etykieta
 * pola, w którym wpisuje się jej wartość.
 *
 * Jednostka celu decyduje o tym, **co** jest wartością serii: przy `seconds`
 * czas, przy `meters` dystans, przy `reps` powtórzenia. Jedno miejsce na tę
 * decyzję = etykieta pola i podsumowanie listy nie rozjadą się.
 */

/** „60 kg × 5”, „60 kg × 45 s”, „45 s”, „30 m”, „60 kg”. */
@Composable
fun setSummary(log: SetLog, unit: RepsUnit): String {
    val weight = log.weightKg.toPlainStringOrNull()
    val value = setValueText(log, unit)
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

/** Wartość serii bez ciężaru: „5 powt.”, „45 s”, „30 m”. */
@Composable
private fun setValueText(log: SetLog, unit: RepsUnit): String? = when (unit) {
    RepsUnit.SECONDS -> log.durationS?.let { stringResource(R.string.session_set_summary_time, it) }
    RepsUnit.METERS -> log.reps?.let { stringResource(R.string.session_set_summary_distance, it) }
    RepsUnit.REPS -> log.reps?.let { stringResource(R.string.session_set_summary_reps, it) }
}

/** Etykieta pola z wartością serii - „Powtórzenia”, „Czas (s)”, „Dystans (m)”. */
@StringRes
fun setValueLabelRes(unit: RepsUnit): Int = when (unit) {
    RepsUnit.REPS -> R.string.session_set_reps
    RepsUnit.SECONDS -> R.string.session_set_time
    RepsUnit.METERS -> R.string.session_set_distance
}
