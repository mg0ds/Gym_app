package com.gymtracker.data.db.rows

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.gymtracker.data.db.entities.SessionExerciseEntity

/** Ćwiczenie w sesji razem z nazwą ćwiczenia z katalogu (JOIN). */
data class SessionExerciseRow(
    @Embedded
    val entry: SessionExerciseEntity,
    @ColumnInfo(name = "exercise_name")
    val exerciseName: String,
    @ColumnInfo(name = "exercise_muscle_group")
    val exerciseMuscleGroup: String?,
)
