package com.gymtracker.data.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gymtracker.data.db.entities.ExerciseMediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Załączniki ćwiczeń (M7).
 *
 * Kolejność wyznacza `position` (rosnąco), a przy remisie `id` - nowe
 * załączniki dopisują się na końcu, a kasowanie nie przenumerowuje reszty:
 * dziura w numerach nic nie psuje, bo i tak sortujemy po `position`, a nie
 * wyliczamy „następny” z liczby wierszy.
 */
@Dao
interface MediaDao {

    @Query("SELECT * FROM exercise_media WHERE exercise_id = :exerciseId ORDER BY position, id")
    fun observeMedia(exerciseId: Long): Flow<List<ExerciseMediaEntity>>

    @Query("SELECT * FROM exercise_media WHERE id = :mediaId")
    suspend fun findById(mediaId: Long): ExerciseMediaEntity?

    /** Najwyższa zajęta pozycja; `-1`, gdy ćwiczenie nie ma jeszcze załączników. */
    @Query(
        "SELECT COALESCE(MAX(position), -1) FROM exercise_media " +
            "WHERE exercise_id = :exerciseId",
    )
    suspend fun maxPosition(exerciseId: Long): Int

    @Insert
    suspend fun insert(media: ExerciseMediaEntity): Long

    @Query("DELETE FROM exercise_media WHERE id = :mediaId")
    suspend fun deleteById(mediaId: Long)

    @Query("UPDATE exercise_media SET caption = :caption WHERE id = :mediaId")
    suspend fun updateCaption(mediaId: Long, caption: String?)

    /**
     * Ścieżki wszystkich własnych miniatur. Sprzątanie (M7) porównuje tę listę
     * z zawartością katalogu `filesDir/thumbs` i kasuje to, czego nie ma
     * w bazie - czyli pliki osierocone po skasowaniu ćwiczenia (kaskada zabiera
     * wiersze, ale nie pliki).
     */
    @Query("SELECT thumbnail_path FROM exercise_media WHERE thumbnail_path IS NOT NULL")
    suspend fun thumbnailPaths(): List<String>
}
