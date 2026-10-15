package com.gymtracker.data.db.rows

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.gymtracker.data.db.entities.PlanExerciseEntity

/**
 * Pozycja dnia planu razem z nazwą ćwiczenia z katalogu.
 *
 * Trzymamy to jako jeden wiersz (JOIN w [com.gymtracker.data.db.daos.PlanDao]),
 * bo lista planu i tak potrzebuje tylko kilku pól ćwiczenia - ładowanie całego
 * [com.gymtracker.data.db.entities.ExerciseEntity] z listami mięśni byłoby
 * niepotrzebną pracą.
 */
data class PlanExerciseRow(
    @Embedded
    val entry: PlanExerciseEntity,
    @ColumnInfo(name = "exercise_name")
    val exerciseName: String,
    @ColumnInfo(name = "exercise_muscle_group")
    val exerciseMuscleGroup: String?,
    @ColumnInfo(name = "exercise_equipment")
    val exerciseEquipment: String?,
)
