package com.gymtracker.data.repo

import androidx.room.withTransaction
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.daos.CalendarDao
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.daos.SessionDao
import com.gymtracker.data.db.daos.SetLogDao
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SessionExerciseEntity
import com.gymtracker.data.db.entities.SetLogEntity
import com.gymtracker.data.db.rows.PlanExerciseRow
import com.gymtracker.data.db.rows.SessionExerciseRow
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.SessionDetails
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.model.WorkoutSession
import com.gymtracker.domain.model.isCompleteFor
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.today
import com.gymtracker.domain.timer.WorkoutTimerState
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
 * żeby nie dało się zakończyć treningu i nie odhaczyć dnia. Od M4 tak samo
 * działa start z dnia planu: jeśli na dziś jest zaplanowany wpis tego dnia,
 * sesja wiąże się z nim (M3 zostawił to jako odroczenie - dopasowanie jest
 * jednoznaczne dzięki `UNIQUE(date, plan_day_id)`).
 *
 * Wszystko, co dzieje się w trakcie treningu (serie, notatka, pauza), zapisujemy
 * od razu: aplikacja może zginąć w każdej chwili, a trening ma zostać.
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
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
                combine(
                    sessionDao.observeSessionExercises(session.id),
                    setLogDao.observeSetsOfSession(session.id),
                ) { rows, sets ->
                    SessionDetails(session.toDomain(), mapExercises(rows, sets))
                }
            }
        }

    override fun observeActiveSessionHead(): Flow<WorkoutSession?> =
        sessionDao.observeActiveSession().map { session -> session?.toDomain() }

    override suspend fun getActiveSession(): WorkoutSession? =
        sessionDao.getActiveSessions().firstOrNull()?.toDomain()

    override fun observeSession(sessionId: Long): Flow<SessionDetails?> = combine(
        sessionDao.observeSession(sessionId),
        sessionDao.observeSessionExercises(sessionId),
        setLogDao.observeSetsOfSession(sessionId),
    ) { session, rows, sets ->
        session?.let { SessionDetails(it.toDomain(), mapExercises(rows, sets)) }
    }

    override suspend fun startFromPlanDay(planDayId: Long): Long? = database.withTransaction {
        val day = planDao.getDay(planDayId) ?: return@withTransaction null
        val sessionId = createSession(
            name = day.name,
            planId = day.planId,
            planDayId = day.id,
            planned = planDao.getDayExercises(day.id).filter { row -> row.entry.isDefaultChoice },
        )

        // Ten sam dzień planu zaplanowany na dziś? Wpis jest najwyżej jeden
        // (UNIQUE(date, plan_day_id)), więc wiążemy go bez zgadywania.
        calendarDao
            .findPlannedFor(date = timeProvider.today().toString(), planDayId = day.id)
            ?.let { entry -> calendarDao.linkSession(entry.id, sessionId) }

        sessionId
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

    override suspend fun setNote(sessionId: Long, note: String?) {
        sessionDao.setNote(sessionId, note?.trim()?.takeIf { cleaned -> cleaned.isNotEmpty() })
    }

    override suspend fun persistTimer(sessionId: Long, state: WorkoutTimerState) {
        sessionDao.setTimerState(
            sessionId = sessionId,
            pausedTotalMs = state.pausedTotalMs,
            pausedAtEpochMs = state.pausedAtEpochMs,
        )
    }

    override suspend fun addSet(sessionExerciseId: Long, draft: SetLogDraft): Long? =
        database.withTransaction {
            val exercise = sessionDao.getSessionExercise(sessionExerciseId)
                ?: return@withTransaction null
            if (!isEditable(exercise.sessionId)) return@withTransaction null
            if (!draft.isCompleteFor(RepsUnit.fromWire(exercise.targetRepsUnit))) {
                return@withTransaction null
            }

            val nextIndex = (setLogDao.maxSetIndex(sessionExerciseId) ?: 0) + 1
            setLogDao.insertSet(
                draft.toEntity(
                    sessionExerciseId = sessionExerciseId,
                    setIndex = nextIndex,
                    completedAtEpochMs = timeProvider.nowEpochMs(),
                ),
            )
        }

    override suspend fun updateSet(setLogId: Long, draft: SetLogDraft): Boolean =
        database.withTransaction {
            val set = setLogDao.getSet(setLogId) ?: return@withTransaction false
            val exercise = sessionDao.getSessionExercise(set.sessionExerciseId)
                ?: return@withTransaction false
            if (!isEditable(exercise.sessionId)) return@withTransaction false
            if (!draft.isCompleteFor(RepsUnit.fromWire(exercise.targetRepsUnit))) {
                return@withTransaction false
            }

            // Numer serii i czas zapisu zostają: poprawiamy pomiar, nie tworzymy
            // nowej serii (inaczej kolejność i historia by się rozjechały).
            setLogDao.updateSet(
                set.copy(
                    weightKg = draft.weightKg,
                    reps = draft.reps,
                    durationS = draft.durationS,
                    isWarmup = draft.isWarmup,
                    isFailure = draft.isFailure,
                ),
            )
            true
        }

    override suspend fun deleteSet(setLogId: Long) = database.withTransaction {
        val set = setLogDao.getSet(setLogId) ?: return@withTransaction
        setLogDao.deleteSet(set)

        // Przenumerowanie od usuniętego numeru w górę. Robimy to pojedynczo
        // i rosnąco, bo `UNIQUE(session_exercise_id, set_index)` nie zniósłby
        // przesunięcia w drugą stronę: 3 → 2 wtedy, gdy 2 jeszcze istnieje.
        setLogDao.getSetsFrom(set.sessionExerciseId, set.setIndex + 1).forEach { stale ->
            setLogDao.updateSet(stale.copy(setIndex = stale.setIndex - 1))
        }
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
                    pausedTotalMs = closedPauseTotal(active, now),
                    pausedAtEpochMs = null,
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
        val now = timeProvider.nowEpochMs()
        sessionDao.updateSession(
            session.copy(
                status = status,
                finishedAtEpochMs = now,
                // Niezmiennik: zamknięty trening nie ma otwartej pauzy. Gdyby
                // użytkownik skończył w pauzie, doliczamy ją i czyścimy znacznik -
                // inaczej `finished - started - pausedTotal` liczyłoby pauzę
                // jako czas treningu.
                pausedTotalMs = closedPauseTotal(session, now),
                pausedAtEpochMs = null,
            ),
        )
        // Zakończony trening odhacza wpis w kalendarzu, z którego powstał.
        // Przerwany nie odhacza - dzień zostaje zaplanowany.
        if (status == SessionEntity.STATUS_FINISHED) {
            calendarDao.markDoneForSession(sessionId)
        }
    }

    /** Suma pauz po domknięciu otwartej pauzy o [now]. */
    private fun closedPauseTotal(session: SessionEntity, now: Long): Long =
        session.pausedTotalMs +
            (session.pausedAtEpochMs?.let { startedAt -> (now - startedAt).coerceAtLeast(0L) } ?: 0L)

    /** Serie dopisujemy tylko do trwającego treningu - historia jest zamknięta. */
    private suspend fun isEditable(sessionId: Long): Boolean =
        sessionDao.getSession(sessionId)?.status == SessionEntity.STATUS_ACTIVE
}

/** Ćwiczenia treningu z doczepionymi seriami (`session_exercise_id` → seria). */
private fun mapExercises(rows: List<SessionExerciseRow>, sets: List<SetLogEntity>): List<SessionExercise> {
    val byExercise = sets.toDomainByExercise()
    return rows.map { row -> row.toDomain(byExercise[row.entry.id].orEmpty()) }
}
