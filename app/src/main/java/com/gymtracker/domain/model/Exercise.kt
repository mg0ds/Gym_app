package com.gymtracker.domain.model

/**
 * Ćwiczenie w formie, w jakiej posługuje się nim reszta aplikacji
 * (UI i logika) - niezależnej od tabel Room.
 */
data class Exercise(
    val id: Long,
    /** Slug z danych startowych; `null` dla ćwiczeń dodanych przez użytkownika. */
    val externalId: String?,
    val name: String,
    /** Nazwa, jakiej użyto w planie źródłowym (bywa inna niż [name]). */
    val nameInProgram: String?,
    val sourceUrl: String?,
    val muscleGroup: String?,
    val muscleGroupSlug: String?,
    val secondaryMuscles: List<String>,
    val equipment: String?,
    val mechanics: String?,
    val forceType: String?,
    val level: String?,
    /** Kroki techniki wykonania („jak to zrobić”); pusta lista, gdy nie ma. */
    val instructions: List<String>,
    /** Link do filmu (znormalizowany YouTube albo dowolny link użytkownika). */
    val youtubeUrl: String?,
    val defaultSets: Int?,
    val defaultRepsMin: Int?,
    val defaultRepsMax: Int?,
    val defaultRestS: Int?,
    /** Co zapisujemy w serii tego ćwiczenia (ciężar, powtórzenia, czas, dystans). */
    val tracking: ExerciseTracking,
    val notes: String?,
    val isFavourite: Boolean,
    val isArchived: Boolean,
) {
    /** Ćwiczenia dodane ręcznie można usunąć; te z danych startowych - zarchiwizować. */
    val isUserCreated: Boolean get() = externalId == null
}

/**
 * Dane formularza „dodaj / edytuj ćwiczenie”. Trzymamy tu surowy link
 * (normalizujemy go dopiero przy zapisie), a puste pola jako `null`.
 */
data class ExerciseDraft(
    val name: String,
    val muscleGroup: String? = null,
    val equipment: String? = null,
    val mechanics: String? = null,
    val level: String? = null,
    val secondaryMuscles: List<String> = emptyList(),
    /** Kroki techniki wykonania (w formularzu jedna linia = jeden krok). */
    val instructions: List<String> = emptyList(),
    val youtubeUrl: String? = null,
    val defaultSets: Int? = null,
    val defaultRepsMin: Int? = null,
    val defaultRepsMax: Int? = null,
    val defaultRestS: Int? = null,
    val tracking: ExerciseTracking = ExerciseTracking.WEIGHT_REPS,
    val notes: String? = null,
)
