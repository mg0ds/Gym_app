package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gymtracker.data.db.entities.SetLogEntity
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
}
