package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gymtracker.data.db.entities.ExerciseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Dostęp do katalogu ćwiczeń.
 *
 * Wyszukiwanie idzie przez tabelę FTS ([com.gymtracker.data.db.entities.ExerciseFtsEntity]):
 * tokenizer składa wielkie litery i usuwa diakrytyki, więc „sztanga” znajdzie
 * „Sztangą”. Zapytanie FTS buduje [com.gymtracker.data.search.ExerciseSearchQuery] -
 * do DAO trafia już gotowy, bezpieczny ciąg (albo null, gdy szukania nie ma).
 */
@Dao
interface ExerciseDao {

    @Query(
        """
        SELECT * FROM exercises
        WHERE is_archived = 0
          AND (:muscleGroup IS NULL OR muscle_group = :muscleGroup)
          AND (:favouritesOnly = 0 OR is_favourite = 1)
          AND (:ftsQuery IS NULL OR id IN (
                SELECT docid FROM exercises_fts WHERE exercises_fts MATCH :ftsQuery
          ))
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeExercises(
        ftsQuery: String?,
        muscleGroup: String?,
        favouritesOnly: Boolean,
    ): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE external_id = :externalId LIMIT 1")
    suspend fun getByExternalId(externalId: String): ExerciseEntity?

    /** Partie mięśniowe obecne w katalogu - źródło filtrów na liście. */
    @Query(
        """
        SELECT DISTINCT muscle_group FROM exercises
        WHERE muscle_group IS NOT NULL AND is_archived = 0
        ORDER BY muscle_group COLLATE NOCASE ASC
        """,
    )
    fun observeMuscleGroups(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM exercises WHERE is_archived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    /**
     * Import danych startowych: wiersze o istniejącym `external_id` są pomijane
     * (zwracają -1), więc nigdy nie nadpisujemy zmian użytkownika.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicates(exercises: List<ExerciseEntity>): List<Long>

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        UPDATE exercises SET is_favourite = :isFavourite, updated_at = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun setFavourite(id: Long, isFavourite: Boolean, updatedAtEpochMs: Long)
}
