package com.gymtracker.domain.model

/**
 * Gdzie dane ćwiczenie jest używane.
 *
 * Potrzebne przy usuwaniu: baza kasuje pozycje ćwiczenia w planach i treningach
 * kaskadą, a użytkownik ma prawo wiedzieć, **co** zniknie razem z nim, zanim
 * potwierdzi. Ćwiczenie, którego nikt nie używa, usuwamy bez ceregieli.
 */
data class ExerciseUsage(
    /** Liczba pozycji w planach treningowych (dni, w których to ćwiczenie występuje). */
    val planPositions: Int = 0,
    /** Liczba pozycji w treningach (także zakończonych - to historia). */
    val sessionPositions: Int = 0,
) {
    val isUsed: Boolean
        get() = planPositions > 0 || sessionPositions > 0
}
