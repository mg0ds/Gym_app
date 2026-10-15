package com.gymtracker.data.repo

import androidx.room.withTransaction
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.daos.SessionDao
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SessionExerciseEntity
import com.gymtracker.domain.model.SessionDetails
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Sesje treningowe.
 *
 * Start z dnia planu **kopiuje** cel ćwiczeń do sesji (nie trzyma referencji do
 * kolejności planu), więc późniejsza edycja planu nie przepisuje rozpoczętego
 * ani zakończonego treningu. Do sesji trafiają tylko wybory domyślne z grup
 * „X albo Y” - alternatywa jest podpowiedzią, a nie osobnym ćwiczeniem.
 *
 * W bazie trwa najwyżej jedna sesja: nowy start zamyka poprzednią jako przerwaną.
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val planDao: PlanDao,
    private val database: AppDatabase,
    private val timeProvider: TimeProvider,
) : SessionRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActiveSession(): Flow<SessionDetails?> =
        sessionDao.observeActiveSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(null)
            } else {
                sessionDao.observeSessionExercises(session.id).map { rows ->
                    SessionDetails(session.toDomain(), rows.map { row -> row.toDomain() })
                }
            }
        }

    override fun observeSession(sessionId: Long): Flow<SessionDetails?> = combine(
        sessionDao.observeSession(sessionId),
        sessionDao.observeSessionExercises(sessionId),
    ) { session, rows ->
        session?.let { SessionDetails(it.toDomain(), rows.map { row -> row.toDomain() }) }
    }

    override suspend fun startFromPlanDay(planDayId: Long): Long? = database.withTransaction {
        val day = planDao.getDay(planDayId) ?: return@withTransaction null
        val planned = planDao.getDayExercises(planDayId)
            .filter { row -> row.entry.isDefaultChoice }
        val now = timeProvider.nowEpochMs()

        // Zabezpieczenie: jeden trwający trening naraz.
        sessionDao.getActiveSessions().forEach { active ->
            sessionDao.updateSession(
                active.copy(
                    status = SessionEntity.STATUS_CANCELLED,
                    finishedAtEpochMs = now,
                ),
            )
        }

        val sessionId = sessionDao.insertSession(
            SessionEntity(
                planId = day.planId,
                planDayId = day.id,
                name = day.name,
                startedAtEpochMs = now,
                status = SessionEntity.STATUS_ACTIVE,
            ),
        )
        sessionDao.insertSessionExercises(
            planned.mapIndexed { index, row ->
                SessionExerciseEntity(
                    sessionId = sessionId,
                    exerciseId = row.entry.exerciseId,
                    position = index + 1,
                    targetSets = row.entry.targetSets,
                    targetRepsMin = row.entry.targetRepsMin,
                    targetRepsMax = row.entry.targetRepsMax,
                    targetRepsUnit = row.entry.targetRepsUnit,
                    restSeconds = row.entry.restSeconds,
                    supersetGroup = row.entry.supersetGroup,
                    alternativeGroup = row.entry.alternativeGroup,
                    isDefaultChoice = true,
                    note = row.entry.note,
                )
            },
        )
        sessionId
    }

    override suspend fun finishSession(sessionId: Long) {
        closeSession(sessionId, SessionEntity.STATUS_FINISHED)
    }

    override suspend fun cancelSession(sessionId: Long) {
        closeSession(sessionId, SessionEntity.STATUS_CANCELLED)
    }

    private suspend fun closeSession(sessionId: Long, status: String) = database.withTransaction {
        val session = sessionDao.getSession(sessionId) ?: return@withTransaction
        if (session.finishedAtEpochMs != null) return@withTransaction
        sessionDao.updateSession(
            session.copy(status = status, finishedAtEpochMs = timeProvider.nowEpochMs()),
        )
    }
}
