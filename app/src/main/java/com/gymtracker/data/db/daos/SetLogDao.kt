package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gymtracker.data.db.entities.SetLogEntity
import com.gymtracker.data.db.rows.LastPerformanceRow
import kotlinx.coroutines.flow.Flow

/**
 * Zalogowane serie treningu.
 *
 * Zapytania idą przez `session_exercises`, bo serie nie mają własnego
 * `session_id` - należą do pozycji treningu, a nie do treningu wprost. Dzięki
 * temu usunięcie ćwiczenia z treningu zabiera jego serie kaskadą, a kolejność
 * serii w zapytaniu pokrywa się z kolejnością ćwiczeń (`position`).
 */
@Dao
interface SetLogDao {

    @Query(
        """
        SELECT sl.* FROM set_logs sl
        JOIN session_exercises se ON se.id = sl.session_exercise_id
        WHERE se.session_id = :sessionId
        ORDER BY se.position, sl.set_index
        """,
    )
    fun observeSetsOfSession(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_logs WHERE id = :setLogId")
    suspend fun getSet(setLogId: Long): SetLogEntity?

    /** Najwyższy numer serii w ćwiczeniu - nowa seria dostaje numer o jeden wyższy. */
    @Query("SELECT MAX(set_index) FROM set_logs WHERE session_exercise_id = :sessionExerciseId")
    suspend fun maxSetIndex(sessionExerciseId: Long): Int?

    /** Serie od wskazanego numeru w górę - do przenumerowania po usunięciu. */
    @Query(
        """
        SELECT * FROM set_logs
        WHERE session_exercise_id = :sessionExerciseId AND set_index >= :fromIndex
        ORDER BY set_index
        """,
    )
    suspend fun getSetsFrom(sessionExerciseId: Long, fromIndex: Int): List<SetLogEntity>

    @Insert
    suspend fun insertSet(set: SetLogEntity): Long

    @Update
    suspend fun updateSet(set: SetLogEntity)

    @Delete
    suspend fun deleteSet(set: SetLogEntity)

    /**
     * Dopisuje do serii faktyczną przerwę, która po niej nastąpiła.
     *
     * To kopia wygodna dla wiersza serii - źródłem prawdy o przerwach jest
     * `rest_intervals` (tam widać powód zakończenia).
     */
    @Query(
        """
        UPDATE set_logs SET rest_after_s = :actualSeconds, rest_target_s = :targetSeconds
        WHERE id = :setLogId
        """,
    )
    suspend fun setRest(setLogId: Long, actualSeconds: Int, targetSeconds: Int)

    /**
     * Serie z **ostatniego zakończonego** treningu każdego z [exerciseIds],
     * z pominięciem bieżącej sesji [sessionId].
     *
     * Dla każdego ćwiczenia bierzemy jeden najnowszy trening (po `started_at`),
     * a nie najnowsze serie w całej historii: podpowiedź „ostatnio” ma pokazać
     * cały przebieg tamtego dnia, także rampę 55/65/75/75/75.
     *
     * Treningi nowsze od bieżącego też odpadają - przy przeglądaniu starszej
     * sesji „ostatnio” nie może znaczyć „później”.
     *
     * Zapytanie jest jednym strzałem dla całej listy ćwiczeń - ekran treningu
     * pyta o kilka do kilkunastu pozycji naraz.
     */
    @Query(
        """
        SELECT l.*, se.exercise_id AS exercise_id,
               s.started_at AS session_started_at, s.session_date AS session_date
        FROM set_logs l
        JOIN session_exercises se ON se.id = l.session_exercise_id
        JOIN sessions s ON s.id = se.session_id
        WHERE se.exercise_id IN (:exerciseIds)
          AND s.status = :status
          AND s.id != :sessionId
          AND s.started_at = (
              SELECT MAX(s2.started_at)
              FROM session_exercises se2
              JOIN sessions s2 ON s2.id = se2.session_id
              WHERE se2.exercise_id = se.exercise_id
                AND s2.status = :status
                AND s2.started_at < (
                    SELECT started_at FROM sessions WHERE id = :sessionId
                )
          )
        ORDER BY se.exercise_id, l.set_index
        """,
    )
    fun observeLastPerformances(
        exerciseIds: List<Long>,
        status: String,
        sessionId: Long,
    ): Flow<List<LastPerformanceRow>>
}
