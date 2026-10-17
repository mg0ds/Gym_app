package com.gymtracker.data.db.rows

import androidx.room.ColumnInfo

/**
 * Podsumowanie jednego zakończonego treningu.
 *
 * Tu agregaty liczy SQL, a nie Kotlin: objętość i liczba serii to proste sumy
 * po `set_logs`, a wierszy w bazie jest tyle, że wciąganie ich wszystkich do
 * pamięci tylko po to, by je zsumować, byłoby marnotrawstwem. Reguła „co jest
 * serią roboczą” siedzi w `CASE WHEN ... is_warmup = 0` i jest pokryta testem
 * repozytorium - tak samo jak filtr zakończonych sesji.
 */
data class SessionAggregateRow(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "working_sets")
    val workingSets: Int,
    @ColumnInfo(name = "volume_kg")
    val volumeKg: Double,
    @ColumnInfo(name = "total_reps")
    val totalReps: Int,
    @ColumnInfo(name = "best_weight_kg")
    val bestWeightKg: Double?,
)
