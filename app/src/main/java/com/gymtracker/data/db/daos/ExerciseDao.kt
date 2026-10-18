package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.rows.ArchiveImpactRow
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

    /**
     * Ćwiczenia z katalogu, czyli **nie**zarchiwizowane. Zarchiwizowany wiersz
     * istnieje dalej (trzyma ze sobą plany i historię), ale dla aplikacji go nie
     * ma: nie da się go otworzyć ani edytować, dopóki nie wróci z archiwum (M9).
     */
    @Query("SELECT * FROM exercises WHERE id = :id AND is_archived = 0")
    fun observeById(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id AND is_archived = 0")
    suspend fun getById(id: Long): ExerciseEntity?

    /**
     * Jak wyżej, ale bez pomijania archiwum. Historia treningów ma zostać
     * czytelna także po usunięciu ćwiczenia z katalogu (M5: notyfikacja końca
     * przerwy mówi, czego dotyczyła).
     */
    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getAnyById(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE external_id = :externalId LIMIT 1")
    suspend fun getByExternalId(externalId: String): ExerciseEntity?

    /**
     * Jak wyżej, ale pomija zarchiwizowane. Używane przy imporcie planu: plan
     * nie powinien odwoływać się do ćwiczenia, które użytkownik usunął
     * z katalogu, bo na ekranie dnia zostałby wiersz-widmo.
     */
    @Query("SELECT * FROM exercises WHERE external_id = :externalId AND is_archived = 0 LIMIT 1")
    suspend fun getActiveByExternalId(externalId: String): ExerciseEntity?

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

    /**
     * Archiwum: ćwiczenia zdjęte z katalogu (M9). Wiersze żyją dalej, bo trzymają
     * plany i historię - tu pokazujemy je użytkownikowi, żeby mógł je przywrócić.
     */
    @Query("SELECT * FROM exercises WHERE is_archived = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeArchived(): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercises WHERE is_archived = 1")
    fun observeArchivedCount(): Flow<Int>

    /**
     * Ile zniknie, gdy archiwum zostanie opróżnione (M9). Jedno zapytanie,
     * cztery liczby - patrz [com.gymtracker.data.db.rows.ArchiveImpactRow].
     */
    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM exercises WHERE is_archived = 1) AS exercises,
          (SELECT COUNT(*) FROM plan_day_exercises
            WHERE exercise_id IN (SELECT id FROM exercises WHERE is_archived = 1)) AS plan_positions,
          (SELECT COUNT(*) FROM session_exercises
            WHERE exercise_id IN (SELECT id FROM exercises WHERE is_archived = 1)) AS session_positions,
          (SELECT COUNT(*) FROM exercise_media
            WHERE exercise_id IN (SELECT id FROM exercises WHERE is_archived = 1)) AS media
        """,
    )
    fun observeArchiveImpact(): Flow<ArchiveImpactRow>

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

    /**
     * Twarde kasowanie. W samym katalogu z niego **nie** korzystamy (patrz
     * [archive]): wiersz ćwiczenia trzyma ze sobą plany i historię treningów,
     * więc usunięcie z katalogu to archiwizacja. Twardo kasujemy tylko
     * z archiwum ([deleteArchived] albo pojedynczy wiersz z ekranu archiwum) -
     * i wtedy użytkownik wie, co robi, bo widzi, ile pozycji w planach i wpisów
     * w historii zniknie razem z nim ([observeArchiveImpact]).
     */
    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Opróżnienie archiwum. Zwraca liczbę skasowanych ćwiczeń (do komunikatu). */
    @Query("DELETE FROM exercises WHERE is_archived = 1")
    suspend fun deleteArchived(): Int

    /** Usunięcie z katalogu bez ruszania planów i historii. */
    @Query("UPDATE exercises SET is_archived = 1, updated_at = :updatedAtEpochMs WHERE id = :id")
    suspend fun archive(id: Long, updatedAtEpochMs: Long)

    /** Powrót z archiwum do katalogu. */
    @Query("UPDATE exercises SET is_archived = 0, updated_at = :updatedAtEpochMs WHERE id = :id")
    suspend fun restore(id: Long, updatedAtEpochMs: Long)

    @Query(
        """
        UPDATE exercises SET is_favourite = :isFavourite, updated_at = :updatedAtEpochMs
        WHERE id = :id
        """,
    )
    suspend fun setFavourite(id: Long, isFavourite: Boolean, updatedAtEpochMs: Long)
}
