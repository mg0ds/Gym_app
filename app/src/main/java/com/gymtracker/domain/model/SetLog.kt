package com.gymtracker.domain.model

/**
 * Zalogowana seria - jedno wykonanie ćwiczenia (fakt, nie plan).
 *
 * [weightKg] jest `null` dla serii bez ciężaru (pompki, plank, bieżnia).
 * [reps] i [durationS] zależą od sposobu zapisu ćwiczenia (`ExerciseTracking`):
 * powtórzenia dla `REPS`/`WEIGHT_REPS`, czas dla `TIME`, oba naraz dla
 * `TIME_REPS`, a metry w [reps] dla `DISTANCE`.
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
    /**
     * Przerwa, która nastąpiła po tej serii (kopia z `rest_intervals`, w sekundach).
     * Źródłem prawdy o przerwach jest timer przerwy - tutaj trzymamy ją dla
     * wygody wiersza serii („60 kg × 5 • przerwa 1:40”).
     */
    val restAfterS: Int? = null,
    /** Ile przerwy było ustawione, gdy seria się kończyła. */
    val restTargetS: Int? = null,
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
 * Czy seria da się zapisać dla sposobu śledzenia [tracking].
 *
 * Ćwiczenie na powtórzenia (i na dystans) wymaga liczby powtórzeń, na czas -
 * czasu, a `TIME_REPS` obu wartości. Ciężar jest zawsze opcjonalny: pompki
 * i deska nie mają go wcale.
 */
fun SetLogDraft.isCompleteFor(tracking: ExerciseTracking): Boolean {
    val hasCount = reps != null && reps > 0
    val hasDuration = durationS != null && durationS > 0
    return when {
        tracking.usesDuration && tracking.usesCount -> hasCount && hasDuration
        tracking.usesDuration -> hasDuration
        else -> hasCount
    }
}
