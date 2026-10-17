package com.gymtracker.domain.model

/**
 * Zalogowana seria - jedno wykonanie ćwiczenia (fakt, nie plan).
 *
 * [weightKg] jest `null` dla serii na czas (plank `60 s`) albo gdy użytkownik
 * nie podał ciężaru (pompki). [reps] i [durationS] są rozłączne i zależą od
 * jednostki celu: `reps` dla powtórzeń i metrów (dystans to też „liczba”),
 * `durationS` dla ćwiczeń na czas.
 */
data class SetLog(
    val id: Long,
    val sessionExerciseId: Long,
    /** Numer serii w ćwiczeniu, od 1 (ciągły - po usunięciu serie się przenumerowują). */
    val setIndex: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationS: Int?,
    val isWarmup: Boolean,
    val isFailure: Boolean,
    val completedAtEpochMs: Long,
)

/**
 * To, co użytkownik wpisał w formularzu serii - dane jeszcze bez numeru
 * i znacznika czasu, które dokłada repozytorium.
 */
data class SetLogDraft(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationS: Int? = null,
    val isWarmup: Boolean = false,
    val isFailure: Boolean = false,
)

/**
 * Czy seria da się zapisać dla celu o jednostce [unit].
 *
 * Ćwiczenie na powtórzenia (i na dystans) wymaga liczby powtórzeń, a na czas -
 * czasu. Ciężar jest zawsze opcjonalny: pompki i plank nie mają go wcale.
 */
fun SetLogDraft.isCompleteFor(unit: RepsUnit): Boolean = when (unit) {
    RepsUnit.SECONDS -> durationS != null && durationS > 0
    RepsUnit.REPS, RepsUnit.METERS -> reps != null && reps > 0
}
