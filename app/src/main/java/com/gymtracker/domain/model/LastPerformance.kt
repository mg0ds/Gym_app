package com.gymtracker.domain.model

import java.time.LocalDate

/**
 * Ostatnie zakończone wykonanie ćwiczenia - podpowiedź na ekranie treningu.
 *
 * Trzymamy **wszystkie serie dnia**, a nie tylko najcięższą, bo przy ćwiczeniu
 * ramped (55/65/75/75/75) najcięższa seria mówi, ile było na końcu, ale nie
 * mówi, od czego zacząć. Pierwsza seria poprzedniego treningu jest najlepszą
 * podpowiedzią dla pierwszej serii dzisiejszej.
 */
data class LastPerformance(
    val date: LocalDate,
    val sets: List<LastPerformanceSet>,
) {
    /** Serie robocze; gdy zapisano tylko rozgrzewkowe - wszystkie. */
    val workingSets: List<LastPerformanceSet>
        get() = sets.filter { set -> !set.isWarmup }.ifEmpty { sets }

    /** Pierwsza seria robocza - start rampy i podpowiedź dla pierwszego wpisu. */
    val firstWorkingSet: LastPerformanceSet? get() = workingSets.firstOrNull()
}

/** Jedna seria z [LastPerformance] - tylko wartości potrzebne do podpowiedzi. */
data class LastPerformanceSet(
    val weightKg: Double?,
    val reps: Int?,
    val durationS: Int?,
    val isWarmup: Boolean,
)
