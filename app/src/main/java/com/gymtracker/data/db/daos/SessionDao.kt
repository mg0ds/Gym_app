package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SessionExerciseEntity
import com.gymtracker.data.db.rows.SessionExerciseRow
import kotlinx.coroutines.flow.Flow

/**
 * Sesje treningowe. W M2 zapisujemy tylko start/koniec i listę ćwiczeń
 * z planu; serie, przerwy i timer (M4/M5) dopisują się do tych samych tabel.
 */
@Dao
interface SessionDao {

    /** Trwający trening (o ile jest) - jeden naraz. */
    @Query("SELECT * FROM sessions WHERE status = 'active' ORDER BY started_at DESC LIMIT 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeSession(sessionId: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY started_at DESC")
    fun observeSessionsByStatus(status: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE status = 'active'")
    suspend fun getActiveSessions(): List<SessionEntity>

    /**
     * Treningi z zakresu dni włącznie (bez przerwanych) - kalendarz pokazuje je
     * obok wpisów w planie, bo trening może istnieć bez wpisu („Nowy trening”
     * z dnia bez planu).
     *
     * Dwie gałęzie, bo dzień treningu to `session_date`, a gdy go brak - dzień
     * startu. Starsze treningi nie mają przypisanej daty, a ich dzień liczy się
     * w strefie urządzenia, więc granice dnia podaje wołający (w ms).
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE status != 'cancelled'
          AND (
            (session_date IS NOT NULL AND session_date BETWEEN :from AND :to)
            OR (session_date IS NULL AND started_at BETWEEN :fromMs AND :toMs)
          )
        ORDER BY started_at
        """,
    )
    fun observeWorkoutsBetween(
        from: String,
        to: String,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<SessionEntity>>

    /** Poprawka dnia treningu („zmień dzień” na ekranie treningu). */
    @Query("UPDATE sessions SET session_date = :date WHERE id = :sessionId")
    suspend fun setSessionDate(sessionId: Long, date: String)

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query(
        """
        SELECT se.*, e.name AS exercise_name, e.muscle_group AS exercise_muscle_group,
               e.default_rest_s AS exercise_default_rest_s
        FROM session_exercises se
        JOIN exercises e ON e.id = se.exercise_id
        WHERE se.session_id = :sessionId
        ORDER BY se.position
        """,
    )
    fun observeSessionExercises(sessionId: Long): Flow<List<SessionExerciseRow>>

    @Query("SELECT * FROM session_exercises WHERE session_id = :sessionId ORDER BY position")
    suspend fun getSessionExercises(sessionId: Long): List<SessionExerciseEntity>

    @Insert
    suspend fun insertSessionExercises(entries: List<SessionExerciseEntity>): List<Long>

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getSessionExercise(id: Long): SessionExerciseEntity?

    @Query("UPDATE sessions SET note = :note WHERE id = :sessionId")
    suspend fun setNote(sessionId: Long, note: String?)

    /**
     * Cel przerwy dla pozycji treningu („zmień przerwę” na karcie ćwiczenia).
     * `NULL` = wróć do domyślnej z katalogu.
     */
    @Query("UPDATE session_exercises SET rest_seconds = :restSeconds WHERE id = :sessionExerciseId")
    suspend fun setExerciseRest(sessionExerciseId: Long, restSeconds: Int?)

    @Query(
        """
        UPDATE sessions
        SET paused_total_ms = :pausedTotalMs, paused_at_ms = :pausedAtEpochMs
        WHERE id = :sessionId
        """,
    )
    suspend fun setTimerState(sessionId: Long, pausedTotalMs: Long, pausedAtEpochMs: Long?)

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int

    /**
     * Liczba pozycji danego ćwiczenia we wszystkich treningach (także
     * zakończonych). Sesja jest kopią, więc usunięcie ćwiczenia z katalogu
     * kasuje kaskadą również te wpisy - użytkownik musi o tym wiedzieć.
     */
    @Query("SELECT COUNT(*) FROM session_exercises WHERE exercise_id = :exerciseId")
    fun observeExerciseUsage(exerciseId: Long): Flow<Int>
}
