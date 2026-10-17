package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gymtracker.data.db.entities.RestIntervalEntity
import kotlinx.coroutines.flow.Flow

/**
 * Przerwy między seriami.
 *
 * `close(...)` jest celowo **idempotentne** (`WHERE ended_at IS NULL`): przerwę
 * może domknąć alarm, powrót użytkownika do aplikacji albo nowa seria - i nie
 * ma znaczenia, kto zdąży pierwszy.
 */
@Dao
interface RestDao {

    @Insert
    suspend fun insert(interval: RestIntervalEntity): Long

    @Query("SELECT * FROM rest_intervals WHERE id = :intervalId")
    suspend fun get(intervalId: Long): RestIntervalEntity?

    /** Trwająca przerwa - najwyżej jedna, pilnuje tego `RestTimer`. */
    @Query("SELECT * FROM rest_intervals WHERE ended_at IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getOpen(): RestIntervalEntity?

    /** Domyka przerwę. Zwraca liczbę zmienionych wierszy (0 = już domknięta). */
    @Query(
        """
        UPDATE rest_intervals
        SET ended_at = :endedAtEpochMs,
            actual_s = :actualSeconds,
            planned_s = :plannedSeconds,
            ended_reason = :reason
        WHERE id = :intervalId AND ended_at IS NULL
        """,
    )
    suspend fun close(
        intervalId: Long,
        endedAtEpochMs: Long,
        actualSeconds: Int,
        plannedSeconds: Int,
        reason: String,
    ): Int

    /** Korekta celu w trakcie odliczania („+15 s”). */
    @Query("UPDATE rest_intervals SET planned_s = :plannedSeconds WHERE id = :intervalId AND ended_at IS NULL")
    suspend fun setPlannedSeconds(intervalId: Long, plannedSeconds: Int)

    /**
     * Przerwy pod statystyki - wszystkie zamknięte przerwy danych ćwiczeń.
     *
     * Filtry („liczy się do średniej”, zakres 10-900 s) są w
     * `RestStatsCalculator`, a nie w SQL-u, żeby jedna, testowalna funkcja
     * odpowiadała na pytanie „co wchodzi do średniej”. Tutaj tylko ograniczamy
     * ilość danych - najnowsze wiersze są tym bardziej użyteczne, im dalej
     * w historię.
     *
     * `LIMIT` dotyczy całego zapytania, a nie każdego ćwiczenia: na ekranie
     * treningu dzielą go między siebie ćwiczenia tego dnia (kilka do kilkunastu
     * pozycji), a to i tak więcej, niż potrzeba do średniej.
     */
    @Query(
        """
        SELECT * FROM rest_intervals
        WHERE exercise_id IN (:exerciseIds) AND ended_at IS NOT NULL
        ORDER BY started_at DESC
        LIMIT :limit
        """,
    )
    fun observeStatsSource(exerciseIds: List<Long>, limit: Int): Flow<List<RestIntervalEntity>>

    /** Wszystkie przerwy treningu (także pominięte) - podsumowanie na ekranie. */
    @Query("SELECT * FROM rest_intervals WHERE session_id = :sessionId ORDER BY started_at")
    fun observeSessionRests(sessionId: Long): Flow<List<RestIntervalEntity>>
}
