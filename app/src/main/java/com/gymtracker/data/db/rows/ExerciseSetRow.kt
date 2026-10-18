package com.gymtracker.data.db.rows

import androidx.room.ColumnInfo

/**
 * Jedna seria jednego ćwiczenia razem z sesją, w której została zapisana.
 *
 * To wiersz **widoku statystyk**, nie encja: agregaty (objętość, 1RM, rekordy)
 * liczy `ExerciseStatsCalculator` w Kotlinie, bo tam da się je przetestować bez
 * bazy. Zapytanie dokłada tylko to, czego w `set_logs` nie ma - datę sesji
 * ([sessionDate], gdy trening należy do zaplanowanego dnia) i chwilę startu.
 */
data class ExerciseSetRow(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "session_started_at")
    val sessionStartedAtEpochMs: Long,
    /** Dzień treningu (ISO); `null` = dzień startu z [sessionStartedAtEpochMs]. */
    @ColumnInfo(name = "session_date")
    val sessionDate: String?,
    @ColumnInfo(name = "set_index")
    val setIndex: Int,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Double?,
    @ColumnInfo(name = "reps")
    val reps: Int?,
    @ColumnInfo(name = "is_warmup")
    val isWarmup: Boolean,
)
