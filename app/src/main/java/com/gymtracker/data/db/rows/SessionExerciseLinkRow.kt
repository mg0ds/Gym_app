package com.gymtracker.data.db.rows

import androidx.room.ColumnInfo

/**
 * Para (trening, ćwiczenie) - po niej historia treningów filtruje się po
 * ćwiczeniu, bez odpytywania bazy raz na każdy wiersz listy.
 */
data class SessionExerciseLinkRow(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
)
