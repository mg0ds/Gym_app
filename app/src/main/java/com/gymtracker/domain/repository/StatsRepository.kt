package com.gymtracker.domain.repository

import com.gymtracker.domain.stats.ExerciseStats
import com.gymtracker.domain.stats.SessionSummary
import com.gymtracker.domain.stats.StatsRange
import kotlinx.coroutines.flow.Flow

/**
 * Historia treningów i statystyki ćwiczenia - tylko do odczytu.
 *
 * Nic tu nie zapisujemy: historia jest skutkiem treningu, a nie osobną
 * czynnością. Jedynym „wejściem” jest wybór zakresu na wykresie.
 */
interface StatsRepository {

    /**
     * Zakończone treningi, najnowsze pierwsze.
     *
     * Filtrowanie (zakres, ćwiczenie, nazwa) zostaje po stronie UI - lista jest
     * krótka, a dzięki temu wybór filtra nie odpytuje bazy od nowa
     * (`SessionSummaryCalculator.filter`).
     */
    fun observeSessionSummaries(): Flow<List<SessionSummary>>

    /**
     * Postęp i rekordy jednego ćwiczenia.
     *
     * [range] zawęża wykres i tabelę, ale **nie rekordy** - rekord nie
     * przestaje być rekordem dlatego, że patrzymy na ostatni miesiąc.
     */
    fun observeExerciseStats(exerciseId: Long, range: StatsRange): Flow<ExerciseStats>
}
