package com.gymtracker.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Ćwiczenie w katalogu (wbudowane z seeda albo dodane przez użytkownika).
 *
 * Klucz naturalny to [externalId] (slug, np. `barbell-bench-press`) - pozwala
 * re-importować dane startowe bez duplikatów i bez nadpisywania zmian
 * użytkownika. Własne ćwiczenia mają [externalId] = null.
 *
 * Listy (mięśnie dodatkowe) trzymamy jako JSON w [secondaryMusclesJson] -
 * to proste i wystarczające, a nie wymaga dodatkowej tabeli.
 */
// `@Serializable`: wiersz jedzie także do kopii zapasowej (M9) - kopią jest
// JSON z nazwami pól takimi jak kolumny (`data/backup/BackupModels.kt`).
@Serializable
@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["external_id"], unique = true, name = "idx_exercises_ext"),
        Index(value = ["muscle_group"], name = "idx_exercises_muscle"),
    ],
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "external_id")
    val externalId: String? = null,
    val name: String,
    /** Jak nazwano ćwiczenie w planie źródłowym (bywa inne niż [name]). */
    @ColumnInfo(name = "name_in_program")
    val nameInProgram: String? = null,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    @ColumnInfo(name = "muscle_group")
    val muscleGroup: String? = null,
    @ColumnInfo(name = "muscle_group_slug")
    val muscleGroupSlug: String? = null,
    @ColumnInfo(name = "secondary_muscles")
    val secondaryMusclesJson: String? = null,
    val equipment: String? = null,
    val mechanics: String? = null,
    @ColumnInfo(name = "force_type")
    val forceType: String? = null,
    val level: String? = null,
    /**
     * Kroki techniki wykonania, jako JSON: `["1. ...","2. ..."]` (M7).
     *
     * Trzymamy je tak samo jak [secondaryMusclesJson] - kroków jest kilka,
     * zawsze w całości, i nigdy nie filtrujemy ich pojedynczo, więc osobna
     * tabela byłaby kosztem bez zysku.
     */
    val instructions: String? = null,
    /** Znormalizowany link `https://www.youtube.com/watch?v=ID`. */
    @ColumnInfo(name = "youtube_url")
    val youtubeUrl: String? = null,
    @ColumnInfo(name = "default_sets")
    val defaultSets: Int? = null,
    @ColumnInfo(name = "default_reps_min")
    val defaultRepsMin: Int? = null,
    @ColumnInfo(name = "default_reps_max")
    val defaultRepsMax: Int? = null,
    @ColumnInfo(name = "default_rest_s")
    val defaultRestS: Int? = null,
    /** Komentarz użytkownika do ćwiczenia. */
    val notes: String? = null,
    @ColumnInfo(name = "is_favourite")
    val isFavourite: Boolean = false,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long,
)
