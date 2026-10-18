package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Załącznik ćwiczenia: zdjęcie albo film **z galerii** (M7).
 *
 * Trzymamy tu **wskazanie**, a nie plik: [remoteUri] to URI z galerii, którym
 * zarządza system - my tylko o nim pamiętamy. Kopia pliku w aplikacji znaczyłaby
 * dwa życia tego samego zdjęcia: użytkownik usuwa je w galerii, a my dalej je
 * pokazujemy i zajmujemy miejsce. Dlatego załącznik znika z aplikacji razem
 * z plikiem w galerii, a nie odwrotnie.
 *
 * Własny jest tylko [thumbnailPath] - mała miniatura w `filesDir`, żeby siatka
 * w szczegółach ćwiczenia była stabilna i szybka, nawet gdy dostęp do URI
 * wygaśnie (albo zdjęcia już nie ma w galerii).
 *
 * `kind` pilnuje słownika w migracji (`CHECK IN ('image','video','youtube','link')`),
 * bo Room nie umie wygenerować `CHECK` z encji (tak samo jak `rest_intervals`).
 */
// `@Serializable`: wiersz jedzie także do kopii zapasowej (M9) - kopią jest
// JSON z nazwami pól takimi jak kolumny (`data/backup/BackupModels.kt`).
@Serializable
@Entity(
    tableName = "exercise_media",
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["exercise_id", "position"], name = "idx_media_exercise"),
    ],
)
data class ExerciseMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
    /** Jedna z wartości `MediaKind` (patrz `domain.model.MediaKind`). */
    val kind: String,
    /** URI z galerii (`content://...`) albo adres linku. */
    @ColumnInfo(name = "remote_uri")
    val remoteUri: String? = null,
    /** Ścieżka **względna** w `filesDir` do własnej miniatury. */
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,
    val caption: String? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long? = null,
    val position: Int,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
)
