package com.gymtracker.data.repo

import androidx.room.withTransaction
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.daos.CalendarDao
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.daos.SessionDao
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SessionExerciseEntity
import com.gymtracker.data.db.rows.PlanExerciseRow
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
 *
 * Start z kalendarza ([startFromSchedule]) dodatkowo wiąże wpis z sesją, a
 * zakończenie treningu odhacza ten wpis jako odbyty - w tej samej transakcji,
 * żeby nie dało się zakończyć treningu i nie odhaczyć dnia.
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val planDao: PlanDao,
    private val calendarDao: CalendarDao,
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
        createSession(
            name = day.name,
            planId = day.planId,
            planDayId = day.id,
            planned = planDao.getDayExercises(day.id).filter { row -> row.entry.isDefaultChoice },
        )
    }

    override suspend fun startFromSchedule(scheduledId: Long): Long? = database.withTransaction {
        val entry = calendarDao.getScheduled(scheduledId) ?: return@withTransaction null
        val day = entry.planDayId?.let { planDayId -> planDao.getDay(planDayId) }

        val sessionId = if (day == null) {
            // Plan zniknął, ale wpis w kalendarzu został - trening ma się dać
            // rozpocząć, tylko bez gotowej listy ćwiczeń.
            createSession(
                name = entry.title,
                planId = entry.planId,
                planDayId = null,
                planned = emptyList(),
            )
        } else {
            createSession(
                name = entry.title,
                planId = day.planId,
                planDayId = day.id,
                planned = planDao.getDayExercises(day.id).filter { row -> row.entry.isDefaultChoice },
            )
        }
        calendarDao.linkSession(entry.id, sessionId)
        sessionId
    }

    override suspend fun finishSession(sessionId: Long) {
        closeSession(sessionId, SessionEntity.STATUS_FINISHED)
    }

    override suspend fun cancelSession(sessionId: Long) {
        closeSession(sessionId, SessionEntity.STATUS_CANCELLED)
    }

    /** Wspólny start: zamyka poprzedni trening i kopiuje cel ćwiczeń do sesji. */
    private suspend fun createSession(
        name: String,
        planId: Long?,
        planDayId: Long?,
        planned: List<PlanExerciseRow>,
    ): Long {
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
                planId = planId,
                planDayId = planDayId,
                name = name,
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
        return sessionId
    }

    private suspend fun closeSession(sessionId: Long, status: String) = database.withTransaction {
        val session = sessionDao.getSession(sessionId) ?: return@withTransaction
        if (session.finishedAtEpochMs != null) return@withTransaction
        sessionDao.updateSession(
            session.copy(status = status, finishedAtEpochMs = timeProvider.nowEpochMs()),
        )
        // Zakończony trening odhacza wpis w kalendarzu, z którego powstał.
        // Przerwany nie odhacza - dzień zostaje zaplanowany.
        if (status == SessionEntity.STATUS_FINISHED) {
            calendarDao.markDoneForSession(sessionId)
        }
    }
}
