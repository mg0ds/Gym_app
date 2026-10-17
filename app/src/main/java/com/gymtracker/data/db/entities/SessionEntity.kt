package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Sesja treningowa (trening, który się dzieje albo się odbył).
 *
 * W M2 sesja powstaje przez „rozpocznij trening z planu” i trzyma tylko czas
 * startu/końca oraz listę ćwiczeń ([SessionExerciseEntity]); M4 dokłada
 * logowanie serii ([SetLogEntity]) i **pauzę**.
 *
 * Pauza jest zapisana, nie tylko trzymana w pamięci: [pausedTotalMs] to suma
 * dotychczasowych pauz, a [pausedAtEpochMs] otwarta pauza (albo `NULL`, gdy
 * zegar idzie). Dzięki temu zabicie aplikacji w trakcie pauzy nie zamienia jej
 * w czas treningu, a czas „pod sztangą” da się policzyć także po restarcie
 * procesu. **Niezmiennik:** sesja zakończona lub przerwana nie ma otwartej
 * pauzy (domykamy ją w `closeSession`).
 *
 * Wskazania na plan i dzień są opcjonalne i „miękkie” (`SET NULL`): usunięcie
 * planu nie może skasować historii treningów.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PlanDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_day_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["plan_id"], name = "idx_sessions_plan"),
        Index(value = ["plan_day_id"], name = "idx_sessions_day"),
        Index(value = ["status"], name = "idx_sessions_status"),
    ],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "plan_id")
    val planId: Long? = null,
    @ColumnInfo(name = "plan_day_id")
    val planDayId: Long? = null,
    val name: String,
    @ColumnInfo(name = "started_at")
    val startedAtEpochMs: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAtEpochMs: Long? = null,
    val status: String,
    val note: String? = null,
    /** Suma zamkniętych pauz w milisekundach. */
    @ColumnInfo(name = "paused_total_ms", defaultValue = "0")
    val pausedTotalMs: Long = 0,
    /** Początek otwartej pauzy albo `NULL`, gdy zegar idzie. */
    @ColumnInfo(name = "paused_at_ms")
    val pausedAtEpochMs: Long? = null,
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_FINISHED = "finished"
        const val STATUS_CANCELLED = "cancelled"
    }
}
