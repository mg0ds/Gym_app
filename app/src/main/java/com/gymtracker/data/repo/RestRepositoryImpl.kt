package com.gymtracker.data.repo

import androidx.room.withTransaction
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.daos.RestDao
import com.gymtracker.data.db.daos.SetLogDao
import com.gymtracker.data.db.entities.RestIntervalEntity
import com.gymtracker.domain.model.RestEndReason
import com.gymtracker.domain.model.RestInterval
import com.gymtracker.domain.repository.RestRepository
import com.gymtracker.domain.stats.RestStats
import com.gymtracker.domain.stats.RestStatsCalculator
import com.gymtracker.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Przerwy między seriami.
 *
 * Czas przerwy liczymy z zegara ściennego - ta sama decyzja co przy treningu
 * (patrz `WorkoutTimerState`), bo przerwa musi przeżyć ubicie procesu, a kotwica
 * monotoniczna żyje tylko w pamięci.
 *
 * Zamknięcie przerwy liczy faktyczny czas **raz**, tutaj: ekran, notyfikacja
 * i statystyki mają patrzeć na jedną liczbę, a nie każdy na swoją.
 */
@Singleton
class RestRepositoryImpl @Inject constructor(
    private val restDao: RestDao,
    private val setLogDao: SetLogDao,
    private val exerciseDao: ExerciseDao,
    private val database: AppDatabase,
    private val timeProvider: TimeProvider,
) : RestRepository {

    override suspend fun start(
        exerciseId: Long,
        sessionId: Long?,
        setLogId: Long?,
        plannedSeconds: Int,
        isAuto: Boolean,
    ): RestInterval {
        val entity = RestIntervalEntity(
            exerciseId = exerciseId,
            sessionId = sessionId,
            setLogId = setLogId,
            startedAtEpochMs = timeProvider.nowEpochMs(),
            plannedSeconds = plannedSeconds,
            isAuto = isAuto,
        )
        return entity.copy(id = restDao.insert(entity)).toDomain()
    }

    override suspend fun setPlannedSeconds(intervalId: Long, plannedSeconds: Int) {
        restDao.setPlannedSeconds(intervalId, plannedSeconds)
    }

    override suspend fun close(
        intervalId: Long,
        reason: RestEndReason,
        plannedSeconds: Int,
    ): Boolean = database.withTransaction {
        val interval = restDao.get(intervalId) ?: return@withTransaction false
        if (interval.endedAtEpochMs != null) return@withTransaction false

        val now = timeProvider.nowEpochMs()
        val actualSeconds = ((now - interval.startedAtEpochMs).coerceAtLeast(0L) / 1000L).toInt()
        val closed = restDao.close(
            intervalId = intervalId,
            endedAtEpochMs = now,
            actualSeconds = actualSeconds,
            plannedSeconds = plannedSeconds,
            reason = reason.wire,
        )
        if (closed == 0) return@withTransaction false

        // Do wiersza serii przepisujemy tylko przerwy, które coś mówią o formie:
        // pominięta i przerwana nie są „przerwą po tej serii”, tylko jej brakiem.
        if (interval.setLogId != null && reason.countsToStats) {
            setLogDao.setRest(interval.setLogId, actualSeconds, plannedSeconds)
        }
        true
    }

    override suspend fun get(intervalId: Long): RestInterval? =
        restDao.get(intervalId)?.toDomain()

    override suspend fun getOpen(): RestInterval? = restDao.getOpen()?.toDomain()

    override fun observeStats(exerciseIds: List<Long>): Flow<Map<Long, RestStats>> =
        if (exerciseIds.isEmpty()) {
            flowOf(emptyMap())
        } else {
            restDao.observeStatsSource(exerciseIds, STATS_ROW_LIMIT).map { rows ->
                rows.map { row -> row.toDomain() }
                    .groupBy { interval -> interval.exerciseId }
                    .mapValues { (_, intervals) -> RestStatsCalculator.calculate(intervals) }
            }
        }

    override fun observeSessionRests(sessionId: Long): Flow<List<RestInterval>> =
        restDao.observeSessionRests(sessionId).map { rows -> rows.map { row -> row.toDomain() } }

    override suspend fun exerciseName(exerciseId: Long): String? =
        exerciseDao.getAnyById(exerciseId)?.name

    private companion object {
        /**
         * Ile najnowszych przerw wchodzi do statystyk ćwiczeń z ekranu.
         *
         * Statystyki liczymy w Kotlinie (mediana, ostatnie treningi), więc
         * potrzebujemy wierszy, a nie agregatów - stąd limit. Dotyczy całego
         * zapytania (`LIMIT` w SQL-u), nie każdego ćwiczenia z osobna: limit
         * dzielą między siebie pozycje jednego treningu. 1000 przerw to kilka
         * lat treningu; starsze i tak nie zmieniają średniej w sposób, który
         * dałoby się zauważyć na ekranie.
         */
        const val STATS_ROW_LIMIT = 1_000
    }
}
