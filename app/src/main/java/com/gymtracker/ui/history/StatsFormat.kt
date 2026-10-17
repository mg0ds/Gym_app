package com.gymtracker.ui.history

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gymtracker.R
import com.gymtracker.domain.stats.ExerciseSetRecord
import com.gymtracker.domain.stats.ExerciseStatsPoint
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.ui.calendar.isoText
import com.gymtracker.ui.components.toPlainStringOrNull
import java.time.LocalDate
import kotlin.math.roundToLong

/**
 * Teksty wspólne dla historii i statystyk.
 *
 * Zakres i opis serii w jednym miejscu, bo pokazują je dwa ekrany (lista
 * treningów i statystyki ćwiczenia) - rozjechany zapis tej samej liczby
 * wygląda jak błąd w danych.
 */

@StringRes
fun StatsRange.labelRes(): Int = when (this) {
    StatsRange.MONTH_1 -> R.string.history_range_1m
    StatsRange.MONTH_3 -> R.string.history_range_3m
    StatsRange.MONTH_6 -> R.string.history_range_6m
    StatsRange.YEAR_1 -> R.string.history_range_1y
    StatsRange.ALL -> R.string.history_range_all
}

/**
 * „12.10.2026”: `12.10.2026` - patrz `ui.calendar.isoText`. Ten sam zapis
 * obowiązuje w kalendarzu, więc data wygląda wszędzie tak samo.
 */

/** Rekord ciężaru: „100 kg × 5 • 12.10.2026”; bez powtórzeń tylko ciężar z datą. */
@Composable
fun recordText(record: ExerciseSetRecord): String =
    setText(weightKg = record.weightKg, reps = record.reps, date = record.date)

/** Ostatnie wykonanie z punktu wykresu: „90 kg × 5 • 12.10.2026”. */
@Composable
fun lastSetText(point: ExerciseStatsPoint): String =
    setText(weightKg = point.topSetKg, reps = point.topSetReps, date = point.date)

/**
 * „117 kg” albo „~117 kg”, gdy 1RM policzono z serii dłuższej niż 12 powtórzeń.
 * Tylko powyżej 12 powtórzeń wzór Epleya przestaje być wiarygodny - i wtedy
 * mówimy o tym wprost, zamiast udawać dokładność.
 */
@Composable
fun oneRepMaxText(record: ExerciseSetRecord): String {
    val value = formatKilograms(record.estimatedOneRepMaxKg ?: 0.0)
    return stringResource(
        if (record.approximate) {
            R.string.exercise_stats_one_rep_max_approximate
        } else {
            R.string.exercise_stats_one_rep_max_value
        },
        value,
    )
}

/**
 * „8 450” - objętości treningu są czterocyfrowe i bez grupowania cyfry zlewają
 * się w jeden ciąg. Spacja nierozdzielająca trzyma liczbę w jednej linii.
 */
fun formatKilograms(kg: Double): String {
    val digits = kg.roundToLong().toString()
    if (digits.length <= 3) return digits
    return digits.reversed().chunked(3).joinToString("\u00A0").reversed()
}

@Composable
private fun setText(weightKg: Double?, reps: Int?, date: LocalDate): String {
    val weight = weightKg.toPlainStringOrNull().orEmpty()
    return if (reps != null) {
        stringResource(R.string.exercise_stats_record_weight_reps, weight, reps, date.isoText())
    } else {
        stringResource(R.string.exercise_stats_record_weight, weight, date.isoText())
    }
}
