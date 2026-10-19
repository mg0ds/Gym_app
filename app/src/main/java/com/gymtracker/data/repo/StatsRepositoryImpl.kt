package com.gymtracker.data.repo

import com.gymtracker.data.db.daos.RestDao
import com.gymtracker.data.db.daos.SessionDao
import com.gymtracker.data.db.daos.StatsDao
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.repository.StatsRepository
import com.gymtracker.domain.stats.ExerciseStats
import com.gymtracker.domain.stats.ExerciseStatsCalculator
import com.gymtracker.domain.stats.SessionSummary
import com.gymtracker.domain.stats.SessionSummaryCalculator
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.today
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Historia treningów i statystyki ćwiczenia.
 *
 * Dwie decyzje warte zapisania:
 * 1. Strefę i „dziś” bierzemy **w momencie liczenia**, nie przy tworzeniu
 *    obiektu - aplikacja żyje dłużej niż jeden dzień, a wykres nie może
 *    zamarznąć na dacie startu procesu.
 * 2. Zapytania oddają surowe serie i przerwy, a agregaty liczy domena
 *    (`ExerciseStatsCalculator`, `SessionSummaryCalculator`) - dzięki temu
 *    reguły mają testy bez bazy, a SQL robi tylko tanie złączenia i sumy.
 */
@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val statsDao: StatsDao,
    private val sessionDao: SessionDao,
    private val restDao: RestDao,
    private val timeProvider: TimeProvider,
) : StatsRepository {

    override fun observeSessionSummaries(): Flow<List<SessionSummary>> = combine(
        sessionDao.observeSessionsByStatus(SessionStatus.FINISHED.wire),
        statsDao.observeSessionAggregates(SessionStatus.FINISHED.wire),
        statsDao.observeClosedRests(StatsLimits.REST_ROWS),
        statsDao.observeSessionExerciseLinks(),
    ) { sessions, aggregates, rests, links ->
        val zone = zone()
        SessionSummaryCalculator.calculate(
            sessions = sessions.map { entity -> entity.toDomain() },
            aggregates = aggregates.map { row -> row.toDomain() },
            rests = rests.map { entity -> entity.toDomain() },
            exerciseIdsBySession = links
                .groupBy({ link -> link.sessionId }, { link -> link.exerciseId })
                .mapValues { (_, exerciseIds) -> exerciseIds.toSet() },
            zone = zone,
            today = timeProvider.today(zone),
        )
    }

    override fun observeExerciseStats(exerciseId: Long, range: StatsRange): Flow<ExerciseStats> = combine(
        statsDao.observeExerciseSets(exerciseId, SessionStatus.FINISHED.wire, StatsLimits.EXERCISE_SET_ROWS),
        restDao.observeStatsSource(listOf(exerciseId), StatsLimits.REST_ROWS),
    ) { sets, rests ->
        val zone = zone()
        ExerciseStatsCalculator.calculate(
            entries = sets.map { row -> row.toEntry(zone) },
            rests = rests.map { entity -> entity.toDomain() },
            range = range,
            today = timeProvider.today(zone),
        )
    }

    private fun zone(): ZoneId = ZoneId.systemDefault()
}
