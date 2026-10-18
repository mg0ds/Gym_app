package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Ćwiczenie w dniu planu: cel (serie, powtórzenia albo czas), przerwa,
 * przynależność do superserii i grupy alternatyw („X albo Y”).
 *
 * * [supersetGroup] - ćwiczenia z tym samym numerem robi się jedno po drugim
 *   bez przerwy; `null` = zwykłe ćwiczenie,
 * * [alternativeGroup] - ten sam tekst łączy ćwiczenia, z których wybiera się
 *   jedno; [isDefaultChoice] wskazuje propozycję domyślną,
 * * [targetRepsUnit] - `reps`, `seconds` albo `meters` (serie na czas, np. deska),
 * * [note] - komentarz do pozycji (z seeda trafia tu np. „Ramped”).
 */
// `@Serializable`: wiersz jedzie także do kopii zapasowej (M9) - kopią jest
// JSON z nazwami pól takimi jak kolumny (`data/backup/BackupModels.kt`).
@Serializable
@Entity(
    tableName = "plan_day_exercises",
    foreignKeys = [
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_day_id"],
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
        Index(value = ["plan_day_id", "position"], name = "idx_pde_day"),
        Index(value = ["exercise_id"], name = "idx_pde_exercise"),
    ],
)
data class PlanExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "plan_day_id")
    val planDayId: Long,
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
    @ColumnInfo(name = "target_weight_kg")
    val targetWeightKg: Double? = null,
    @ColumnInfo(name = "rest_seconds")
    val restSeconds: Int? = null,
    @ColumnInfo(name = "superset_group")
    val supersetGroup: Int? = null,
    @ColumnInfo(name = "alternative_group")
    val alternativeGroup: String? = null,
    @ColumnInfo(name = "is_default_choice")
    val isDefaultChoice: Boolean = true,
    val note: String? = null,
) {
    companion object {
        const val UNIT_REPS = "reps"
        const val UNIT_SECONDS = "seconds"
        const val UNIT_METERS = "meters"
    }
}
