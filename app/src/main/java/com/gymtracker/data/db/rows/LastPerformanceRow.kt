package com.gymtracker.data.db.rows

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.gymtracker.data.db.entities.SetLogEntity

/**
 * Ostatnia zakończona seria danego ćwiczenia wraz z treningiem, z którego
 * pochodzi. Wiersz widoku „co robiłem ostatnio”, a nie encja.
 */
data class LastPerformanceRow(
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
    @ColumnInfo(name = "session_started_at")
    val sessionStartedAtEpochMs: Long,
    /** Dzień treningu (ISO); `null` = dzień startu. */
    @ColumnInfo(name = "session_date")
    val sessionDate: String?,
    @Embedded
    val set: SetLogEntity,
)
