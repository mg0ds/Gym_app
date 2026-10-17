package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Zalogowana seria - jedno wykonanie ćwiczenia w treningu.
 *
 * Wiersz trzyma **fakt**, nie plan: serie są dowodem wykonania, a nie celem.
 * Dlatego `weight_kg` bywa `NULL` (seria na czas, np. plank `60 s`), a `reps`
 * i `duration_s` są rozłączne: powtórzenia dla `reps`, sekundy dla `seconds`.
 *
 * [setIndex] numeruje serie w obrębie pozycji treningu i jest unikalny
 * (`UNIQUE(session_exercise_id, set_index)`), więc dwa równoległe zapisy nie
 * nadpiszą się nawzajem. Numeracja jest ciągła od 1 - usunięcie serii
 * przenumerowuje resztę (robimy to w transakcji, patrz `SessionRepositoryImpl`),
 * bo dla użytkownika liczy się „seria 1, 2, 3”, a nie dziury po skasowanych.
 *
 * [restAfterS] i [restTargetS] wypełnia timer przerwy (M5); w M4 zostają `NULL`.
 */
@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = ["session_exercise_id", "set_index"],
            unique = true,
            name = "idx_set_logs_uniq",
        ),
    ],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_exercise_id")
    val sessionExerciseId: Long,
    /** Numer serii w obrębie ćwiczenia treningu, od 1. */
    @ColumnInfo(name = "set_index")
    val setIndex: Int,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double? = null,
    val reps: Int? = null,
    @ColumnInfo(name = "duration_s")
    val durationS: Int? = null,
    val rpe: Double? = null,
    @ColumnInfo(name = "is_warmup")
    val isWarmup: Boolean = false,
    @ColumnInfo(name = "is_failure")
    val isFailure: Boolean = false,
    @ColumnInfo(name = "rest_after_s")
    val restAfterS: Int? = null,
    @ColumnInfo(name = "rest_target_s")
    val restTargetS: Int? = null,
    @ColumnInfo(name = "completed_at")
    val completedAtEpochMs: Long,
)
