package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Ćwiczenie w sesji - kopia pozycji z dnia planu zrobiona w momencie startu.
 *
 * Kopiujemy cel zamiast czytać go z planu, żeby zmiana planu nie przepisywała
 * historii: trening sprzed zmiany ma zostać taki, jaki był wtedy zaplanowany.
 */
// `@Serializable`: wiersz jedzie także do kopii zapasowej (M9) - kopią jest
// JSON z nazwami pól takimi jak kolumny (`data/backup/BackupModels.kt`).
@Serializable
@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["session_id", "position"], name = "idx_session_exercises_pos"),
        Index(value = ["exercise_id"], name = "idx_session_exercises_exercise"),
    ],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "target_sets")
    val targetSets: Int? = null,
    @ColumnInfo(name = "target_reps_min")
    val targetRepsMin: Int? = null,
    @ColumnInfo(name = "target_reps_max")
    val targetRepsMax: Int? = null,
    @ColumnInfo(name = "target_reps_unit")
    val targetRepsUnit: String = PlanExerciseEntity.UNIT_REPS,
    @ColumnInfo(name = "rest_seconds")
    val restSeconds: Int? = null,
    @ColumnInfo(name = "superset_group")
    val supersetGroup: Int? = null,
    @ColumnInfo(name = "alternative_group")
    val alternativeGroup: String? = null,
    @ColumnInfo(name = "is_default_choice")
    val isDefaultChoice: Boolean = true,
    val note: String? = null,
)
