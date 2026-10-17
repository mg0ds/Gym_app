package com.gymtracker.data.db.rows

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.gymtracker.data.db.entities.SessionExerciseEntity

/** Ćwiczenie w sesji razem z nazwą i domyślną przerwą z katalogu (JOIN). */
data class SessionExerciseRow(
    @Embedded
    val entry: SessionExerciseEntity,
    @ColumnInfo(name = "exercise_name")
    val exerciseName: String,
    @ColumnInfo(name = "exercise_muscle_group")
    val exerciseMuscleGroup: String?,
    /**
     * Domyślna przerwa z karty ćwiczenia - druga w kolejności po celu z planu,
     * a przed ustawieniem globalnym (`SessionExercise.restSeconds`).
     */
    @ColumnInfo(name = "exercise_default_rest_s")
    val exerciseDefaultRestSeconds: Int?,
)
