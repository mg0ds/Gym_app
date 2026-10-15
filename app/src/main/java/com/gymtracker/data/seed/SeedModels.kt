package com.gymtracker.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modele plików z danymi startowymi (katalog `assets/seed/`).
 *
 * Pliki są generowane poza aplikacją, dlatego odczyt jest wyrozumiały:
 * nieznane pola ignorujemy, a nowe opcjonalne pola nie wywracają importu.
 */
@Serializable
internal data class ExercisesSeedFile(
    @SerialName("schema_version")
    val schemaVersion: Int,
    @SerialName("generated_at")
    val generatedAt: String,
    val exercises: List<SeedExercise>,
)

@Serializable
internal data class SeedExercise(
    val id: String,
    val name: String,
    @SerialName("source_url")
    val sourceUrl: String? = null,
    @SerialName("muscle_group")
    val muscleGroup: String? = null,
    @SerialName("muscle_group_slug")
    val muscleGroupSlug: String? = null,
    @SerialName("secondary_muscles")
    val secondaryMuscles: List<String> = emptyList(),
    val equipment: String? = null,
    val mechanics: String? = null,
    val level: String? = null,
    @SerialName("youtube_id")
    val youtubeId: String? = null,
    @SerialName("name_in_program")
    val nameInProgram: String? = null,
)

/** Ścieżki w assetach - jedno miejsce, z którego korzysta import. */
internal object SeedFiles {
    const val EXERCISES = "seed/exercises.json"
    const val PROGRAMS_DIR = "seed/programs"

    /** Klucz w `app_meta` z wersją zaimportowanego katalogu ćwiczeń. */
    const val META_EXERCISES_VERSION = "exercises_seed_version"
}
