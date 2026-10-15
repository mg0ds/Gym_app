package com.gymtracker.domain.model

/**
 * Gdzie dane ćwiczenie jest używane.
 *
 * Potrzebne przy usuwaniu: ćwiczenie znika z katalogu, ale jego pozycje
 * w planach i treningach zostają (§6.11), a użytkownik ma prawo wiedzieć,
 * **ile** ich jest, zanim potwierdzi.
 */
data class ExerciseUsage(
    /** Liczba pozycji w planach treningowych (dni, w których to ćwiczenie występuje). */
    val planPositions: Int = 0,
    /** Liczba pozycji w treningach (także zakończonych - to historia). */
    val sessionPositions: Int = 0,
)
