package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Query
import com.gymtracker.data.db.entities.RestIntervalEntity
import com.gymtracker.data.db.rows.ExerciseSetRow
import com.gymtracker.data.db.rows.SessionAggregateRow
import com.gymtracker.data.db.rows.SessionExerciseLinkRow
import kotlinx.coroutines.flow.Flow

/**
 * Zapytania pod historię i statystyki (M6).
 *
 * Podział pracy jest celowy: SQL robi to, co umie tanio (złączanie tabel,
 * zakończone sesje, sumy per trening), a **reguły** - co jest serią roboczą,
 * jak liczy się 1RM, co wchodzi do średniej przerwy, jaki jest zakres dat -
 * liczy Kotlin w `ExerciseStatsCalculator` i `RestStatsCalculator`. Dzięki
 * temu reguły mają testy bez bazy, a zapytania nie muszą być testowane na
 * każdą zmianę progu (10-900 s, 12 powtórzeń).
 */
@Dao
interface StatsDao {

    /**
     * Serie jednego ćwiczenia ze wszystkich zakończonych treningów.
     *
     * Sortowanie od najnowszych i `LIMIT` po stronie SQL: przy kilku latach
     * treningu interesuje nas postęp, a nie archiwum - starsze wiersze
     * wypadają pierwsze. [limit] dotyczy całego zapytania.
     */
    @Query(
        """
        SELECT l.set_index AS set_index,
               l.weight_kg AS weight_kg,
               l.reps AS reps,
               l.duration_s AS duration_s,
               l.is_warmup AS is_warmup,
               s.id AS session_id,
               s.started_at AS session_started_at,
               s.session_date AS session_date
        FROM set_logs l
        JOIN session_exercises se ON se.id = l.session_exercise_id
        JOIN sessions s ON s.id = se.session_id
        WHERE se.exercise_id = :exerciseId AND s.status = :status
        ORDER BY s.started_at DESC, l.set_index
        LIMIT :limit
        """,
    )
    fun observeExerciseSets(exerciseId: Long, status: String, limit: Int): Flow<List<ExerciseSetRow>>

    /**
     * Podsumowanie każdego zakończonego treningu: serie robocze, objętość
     * i najcięższa seria. Jeden wiersz na trening - lista historii nie zależy
     * więc od liczby zapisanych serii.
     */
    @Query(
        """
        SELECT se.session_id AS session_id,
               SUM(CASE WHEN l.is_warmup = 0 THEN 1 ELSE 0 END) AS working_sets,
               SUM(CASE WHEN l.is_warmup = 0
                        THEN COALESCE(l.weight_kg, 0.0) * COALESCE(l.reps, 0)
                        ELSE 0.0 END) AS volume_kg,
               SUM(CASE WHEN l.is_warmup = 0 THEN COALESCE(l.reps, 0) ELSE 0 END) AS total_reps,
               MAX(CASE WHEN l.is_warmup = 0 THEN l.weight_kg END) AS best_weight_kg
        FROM set_logs l
        JOIN session_exercises se ON se.id = l.session_exercise_id
        JOIN sessions s ON s.id = se.session_id
        WHERE s.status = :status
        GROUP BY se.session_id
        """,
    )
    fun observeSessionAggregates(status: String): Flow<List<SessionAggregateRow>>

    /**
     * Pary (trening, ćwiczenie) - filtr historii po ćwiczeniu działa na tej
     * mapie, a nie na osobnym zapytaniu dla każdego wiersza listy.
     *
     * Zawiera też treningi w toku i przerwane; odsiewa je dopiero lista
     * (i tak pokazuje tylko zakończone), więc zapytanie nie musi znać statusu.
     */
    @Query("SELECT session_id, exercise_id FROM session_exercises")
    fun observeSessionExerciseLinks(): Flow<List<SessionExerciseLinkRow>>

    /**
     * Zamknięte przerwy przypisane do treningu - pod średnią przerwę przy
     * każdym wierszu historii.
     *
     * Filtry („co wchodzi do średniej”, 10-900 s) są w `RestStatsCalculator`.
     * Limit bierze najnowsze przerwy: starsze i tak nie zmienią średniej
     * w sposób widoczny na liście.
     */
    @Query(
        """
        SELECT * FROM rest_intervals
        WHERE session_id IS NOT NULL AND ended_at IS NOT NULL
        ORDER BY started_at DESC
        LIMIT :limit
        """,
    )
    fun observeClosedRests(limit: Int): Flow<List<RestIntervalEntity>>
}
