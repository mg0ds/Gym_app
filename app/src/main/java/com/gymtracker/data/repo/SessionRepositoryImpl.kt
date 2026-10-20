package com.gymtracker.data.repo

import androidx.room.withTransaction
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.daos.CalendarDao
import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.daos.PlanDao
import com.gymtracker.data.db.daos.SessionDao
import com.gymtracker.data.db.daos.SetLogDao
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SessionExerciseEntity
import com.gymtracker.data.db.entities.SetLogEntity
import com.gymtracker.data.db.rows.PlanExerciseRow
import com.gymtracker.data.db.rows.SessionExerciseRow
import com.gymtracker.domain.model.DayWorkout
import com.gymtracker.domain.model.ExerciseTracking
import com.gymtracker.domain.model.LastPerformance
import com.gymtracker.domain.model.LastPerformanceSet
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.SessionDetails
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.model.WorkoutSession
import com.gymtracker.domain.model.isCompleteFor
import com.gymtracker.domain.repository.SessionRepository
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.startOfDayEpochMs
import com.gymtracker.domain.time.today
import com.gymtracker.domain.timer.WorkoutTimerState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
 *
 * Po fakcie zmienia się tylko dzień treningu ([setSessionDate]): wpis w
 * kalendarzu idzie wtedy razem z treningiem, żeby „odbyty” nie został na dniu,
 * w którym nic się nie stało.
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
    private val planDao: PlanDao,
    private val calendarDao: CalendarDao,
    private val exerciseDao: ExerciseDao,
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

    override fun observeLastPerformances(
        sessionId: Long,
        exerciseIds: List<Long>,
    ): Flow<Map<Long, LastPerformance>> =
        if (exerciseIds.isEmpty()) {
            flowOf(emptyMap())
        } else {
            setLogDao
                .observeLastPerformances(
                    exerciseIds = exerciseIds,
                    status = SessionStatus.FINISHED.wire,
                    sessionId = sessionId,
                )
                .map { rows ->
                    rows.groupBy { row -> row.exerciseId }.mapValues { (_, exerciseSets) ->
                        val first = exerciseSets.first()
                        LastPerformance(
                            date = first.sessionDate
                                ?.let { value -> LocalDate.parse(value) }
                                ?: Instant.ofEpochMilli(first.sessionStartedAtEpochMs)
                                    .atZone(zone())
                                    .toLocalDate(),
                            sets = exerciseSets.map { row ->
                                LastPerformanceSet(
                                    weightKg = row.set.weightKg,
                                    reps = row.set.reps,
                                    durationS = row.set.durationS,
                                    isWarmup = row.set.isWarmup,
                                )
                            },
                        )
                    }
                }
        }

    override fun observeWorkoutsBetween(from: LocalDate, to: LocalDate): Flow<List<DayWorkout>> {
        val zone = zone()
        return sessionDao.observeWorkoutsBetween(
            from = from.toString(),
            to = to.toString(),
            fromMs = from.startOfDayEpochMs(zone),
            // Dzień końcowy włącznie, więc górna granica to początek następnego.
            toMs = to.plusDays(1).startOfDayEpochMs(zone),
        ).map { rows -> rows.map { row -> row.toDayWorkout(zone) } }
    }

    override suspend fun startFromPlanDay(planDayId: Long): Long? = database.withTransaction {
        val day = planDao.getDay(planDayId) ?: return@withTransaction null
        val planned = planDao.getDayExercises(day.id).filter { row -> row.entry.isDefaultChoice }

        // Ten sam dzień planu zaplanowany na dziś? Wpis jest najwyżej jeden
        // (UNIQUE(date, plan_day_id)), więc wiążemy go bez zgadywania.
        val todayEntry = calendarDao.findPlannedFor(date = timeProvider.today().toString(), planDayId = day.id)
        val sessionId = createSession(
            name = day.name,
            planId = day.planId,
            planDayId = day.id,
            planned = planned,
            // Start z planu należy do dnia, w którym się zaczął - chyba że na
            // dziś czeka zaplanowany wpis; wtedy trening idzie pod jego datę.
            sessionDate = todayEntry?.date?.let { value -> LocalDate.parse(value) },
        )

        todayEntry?.let { entry -> calendarDao.linkSession(entry.id, sessionId) }
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
                sessionDate = LocalDate.parse(entry.date),
            )
        } else {
            createSession(
                name = entry.title,
                planId = day.planId,
                planDayId = day.id,
                planned = planDao.getDayExercises(day.id).filter { row -> row.entry.isDefaultChoice },
                sessionDate = LocalDate.parse(entry.date),
            )
        }
        calendarDao.linkSession(entry.id, sessionId)
        sessionId
    }

    override suspend fun startForDay(date: LocalDate, planDayId: Long?, name: String): Long? =
        database.withTransaction {
            val day = planDayId?.let { id -> planDao.getDay(id) }
            val sessionId = createSession(
                name = day?.name ?: name,
                planId = day?.planId,
                planDayId = day?.id,
                planned = day?.let { chosen -> planDao.getDayExercises(chosen.id) }
                    .orEmpty()
                    .filter { row -> row.entry.isDefaultChoice },
                sessionDate = date,
            )
            // Dzień planu wybrany ręcznie wiążemy z wpisem, gdy taki czeka -
            // inaczej „zaplanowane” zostałoby na zawsze obok odbytego treningu.
            day?.let { chosen ->
                calendarDao
                    .findPlannedFor(date = date.toString(), planDayId = chosen.id)
                    ?.let { entry -> calendarDao.linkSession(entry.id, sessionId) }
            }
            sessionId
        }

    override suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long): Boolean =
        database.withTransaction {
            if (!isEditable(sessionId)) return@withTransaction false
            val exercise = exerciseDao.getById(exerciseId) ?: return@withTransaction false
            val tracking = ExerciseTracking.fromWire(exercise.tracking)
            val lastPosition = sessionDao.getSessionExercises(sessionId).maxOfOrNull { it.position } ?: 0
            sessionDao.insertSessionExercises(
                listOf(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        position = lastPosition + 1,
                        targetRepsUnit = tracking.unit.wire,
                        tracking = tracking.wire,
                    ),
                ),
            )
            true
        }

    override suspend fun setSessionDate(sessionId: Long, date: LocalDate) = database.withTransaction {
        val session = sessionDao.getSession(sessionId) ?: return@withTransaction
        // Trwający trening ma dzień wybrany przy starcie; poprawiamy po fakcie.
        if (session.status != SessionEntity.STATUS_FINISHED) return@withTransaction
        sessionDao.setSessionDate(sessionId, date.toString())

        // Wpis, który ten trening odhaczył, idzie za nim - inaczej „odbyty”
        // zostałby na dniu, w którym nic się nie stało.
        val entry = calendarDao.findBySession(sessionId) ?: return@withTransaction
        val planDayId = entry.planDayId
        val taken = planDayId != null && calendarDao.getScheduledOn(date.toString())
            .any { other -> other.id != entry.id && other.planDayId == planDayId }
        if (taken) {
            calendarDao.unlinkSession(sessionId)
        } else {
            calendarDao.setDate(entry.id, date.toString())
        }
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
            if (!draft.isCompleteFor(ExerciseTracking.fromWire(exercise.tracking))) {
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
            if (!draft.isCompleteFor(ExerciseTracking.fromWire(exercise.tracking))) {
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

    override suspend fun setExerciseRest(sessionExerciseId: Long, restSeconds: Int?) {
        val exercise = sessionDao.getSessionExercise(sessionExerciseId) ?: return
        if (!isEditable(exercise.sessionId)) return
        // Zero i wartości ujemne znaczą „bez przerwy”, a nie „przerwa ujemna” -
        // do bazy idzie wtedy NULL, czyli powrót do domyślnej.
        sessionDao.setExerciseRest(sessionExerciseId, restSeconds?.takeIf { seconds -> seconds > 0 })
    }

    /** Wspólny start: zamyka poprzedni trening i kopiuje cel ćwiczeń do sesji. */
    private suspend fun createSession(
        name: String,
        planId: Long?,
        planDayId: Long?,
        planned: List<PlanExerciseRow>,
        sessionDate: LocalDate? = null,
    ): Long {
        val now = timeProvider.nowEpochMs()

        // Zabezpieczenie: jeden trwający trening naraz. Poprzedni zamykamy jako
        // przerwany i odczepiamy od wpisu, żeby wpis nie wskazywał na trening,
        // który się nie odbył.
        sessionDao.getActiveSessions().forEach { active ->
            sessionDao.updateSession(
                active.copy(
                    status = SessionEntity.STATUS_CANCELLED,
                    finishedAtEpochMs = now,
                    pausedTotalMs = closedPauseTotal(active, now),
                    pausedAtEpochMs = null,
                ),
            )
            calendarDao.detachSession(active.id)
        }

        val sessionId = sessionDao.insertSession(
            SessionEntity(
                planId = planId,
                planDayId = planDayId,
                name = name,
                sessionDate = sessionDate?.toString(),
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
                    tracking = ExerciseTracking.fromWire(row.exerciseTracking)
                        .forTarget(RepsUnit.fromWire(row.entry.targetRepsUnit))
                        .wire,
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
        // Przerwany wpisu nie odhacza, ale też się od niego odczepia: wpis ma
        // wrócić do stanu sprzed startu, a nie wskazywać na trening, który się
        // nie odbył (niezmiennik: `scheduled_sessions.session_id` celuje tylko
        // w zakończony trening, a NULL - w zaplanowany lub pominięty).
        if (status == SessionEntity.STATUS_FINISHED) {
            calendarDao.markDoneForSession(sessionId)
        } else {
            calendarDao.detachSession(sessionId)
        }
    }

    /** Suma pauz po domknięciu otwartej pauzy o [now]. */
    private fun closedPauseTotal(session: SessionEntity, now: Long): Long =
        session.pausedTotalMs +
            (session.pausedAtEpochMs?.let { startedAt -> (now - startedAt).coerceAtLeast(0L) } ?: 0L)

    /** Serie dopisujemy tylko do trwającego treningu - historia jest zamknięta. */
    private suspend fun isEditable(sessionId: Long): Boolean =
        sessionDao.getSession(sessionId)?.status == SessionEntity.STATUS_ACTIVE

    /** Strefa urządzenia - dzień treningu liczymy tak samo, jak go pokazujemy. */
    private fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Ćwiczenia treningu z doczepionymi seriami (`session_exercise_id` → seria). */
private fun mapExercises(rows: List<SessionExerciseRow>, sets: List<SetLogEntity>): List<SessionExercise> {
    val byExercise = sets.toDomainByExercise()
    return rows.map { row -> row.toDomain(byExercise[row.entry.id].orEmpty()) }
}
