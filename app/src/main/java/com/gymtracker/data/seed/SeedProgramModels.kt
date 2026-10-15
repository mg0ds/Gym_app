package com.gymtracker.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modele pliku planu (katalog `assets/seed/programs/`, pliki JSON).
 *
 * Podobnie jak przy katalogu ćwiczeń: nieznane pola ignorujemy, a brakujące
 * opcjonalne nie wywracają importu. Ćwiczenia wskazujemy po `exercise_id`
 * (slug z katalogu) - to jedyne wiązanie między planem a ćwiczeniami.
 */
@Serializable
internal data class ProgramSeedFile(
    @SerialName("schema_version")
    val schemaVersion: Int,
    @SerialName("generated_at")
    val generatedAt: String,
    val program: SeedProgram,
)

@Serializable
internal data class SeedProgram(
    val id: String,
    val name: String,
    @SerialName("source_url")
    val sourceUrl: String? = null,
    val author: String? = null,
    /** Przypisanie dni do dni tygodnia (podpowiedź dla kalendarza w M3). */
    val schedule: List<SeedScheduleEntry> = emptyList(),
    val workouts: List<SeedWorkout> = emptyList(),
)

@Serializable
internal data class SeedScheduleEntry(
    @SerialName("day_label")
    val dayLabel: String? = null,
    @SerialName("day_of_week")
    val dayOfWeek: Int? = null,
    @SerialName("workout_id")
    val workoutId: String,
)

@Serializable
internal data class SeedWorkout(
    val id: String,
    val name: String,
    val order: Int = 0,
    val exercises: List<SeedWorkoutExercise> = emptyList(),
)

@Serializable
internal data class SeedWorkoutExercise(
    val order: Int = 0,
    @SerialName("exercise_id")
    val exerciseId: String,
    val name: String? = null,
    val sets: Int? = null,
    @SerialName("reps_min")
    val repsMin: Int? = null,
    @SerialName("reps_max")
    val repsMax: Int? = null,
    @SerialName("reps_raw")
    val repsRaw: String? = null,
    /** Dopisek do pozycji, np. „Ramped” - trafia do komentarza w planie. */
    val modifier: String? = null,
    /** Warianty do wyboru („Pull Ups albo Inverted Rows”). */
    val alternatives: List<SeedAlternative> = emptyList(),
    @SerialName("choice_note")
    val choiceNote: String? = null,
    /** `reps = "time"` oznacza serię na czas (np. deska 60 s). */
    val tracking: SeedTracking? = null,
)

@Serializable
internal data class SeedAlternative(
    val name: String,
    @SerialName("exercise_id")
    val exerciseId: String,
)

@Serializable
internal data class SeedTracking(
    val reps: String? = null,
    val unit: String? = null,
)
