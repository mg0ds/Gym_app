package com.gymtracker.ui.exercises

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gymtracker.R

/**
 * Tłumaczenia słownika katalogu na język interfejsu.
 *
 * W bazie i w danych startowych trzymamy wartości w jednym języku (np. `Chest`,
 * `Barbell`) - inaczej filtry, import i wyszukiwanie rozjechałyby się przy
 * zmianie języka aplikacji. Tutaj zamieniamy je na etykiety do pokazania;
 * nieznanej wartości nie gubimy, tylko pokazujemy tak, jak przyszła.
 */
@Composable
fun muscleGroupLabel(value: String?): String? = value?.let {
    when (it) {
        "Chest" -> stringResource(R.string.muscle_chest)
        "Lats" -> stringResource(R.string.muscle_lats)
        "Upper Back" -> stringResource(R.string.muscle_upper_back)
        "Lower Back" -> stringResource(R.string.muscle_lower_back)
        "Traps" -> stringResource(R.string.muscle_traps)
        "Shoulders" -> stringResource(R.string.muscle_shoulders)
        "Biceps" -> stringResource(R.string.muscle_biceps)
        "Triceps" -> stringResource(R.string.muscle_triceps)
        "Forearms" -> stringResource(R.string.muscle_forearms)
        "Abs" -> stringResource(R.string.muscle_abs)
        "Glutes" -> stringResource(R.string.muscle_glutes)
        "Quads" -> stringResource(R.string.muscle_quads)
        "Hamstrings" -> stringResource(R.string.muscle_hamstrings)
        "Calves" -> stringResource(R.string.muscle_calves)
        "Cardio" -> stringResource(R.string.muscle_cardio)
        "Other" -> stringResource(R.string.value_other)
        else -> it
    }
}

@Composable
fun equipmentLabel(value: String?): String? = value?.let {
    when (it) {
        "Barbell" -> stringResource(R.string.equipment_barbell)
        "Dumbbell" -> stringResource(R.string.equipment_dumbbell)
        "EZ Bar" -> stringResource(R.string.equipment_ez_bar)
        "Cable" -> stringResource(R.string.equipment_cable)
        "Machine" -> stringResource(R.string.equipment_machine)
        "Bodyweight" -> stringResource(R.string.equipment_bodyweight)
        "Kettlebell" -> stringResource(R.string.equipment_kettlebell)
        "Bands" -> stringResource(R.string.equipment_bands)
        "Other" -> stringResource(R.string.value_other)
        else -> it
    }
}

@Composable
fun mechanicsLabel(value: String?): String? = value?.let {
    when (it) {
        "Compound" -> stringResource(R.string.mechanics_compound)
        "Isolation" -> stringResource(R.string.mechanics_isolation)
        else -> it
    }
}

@Composable
fun levelLabel(value: String?): String? = value?.let {
    when (it) {
        "Beginner" -> stringResource(R.string.level_beginner)
        "Intermediate" -> stringResource(R.string.level_intermediate)
        "Advanced" -> stringResource(R.string.level_advanced)
        else -> it
    }
}
