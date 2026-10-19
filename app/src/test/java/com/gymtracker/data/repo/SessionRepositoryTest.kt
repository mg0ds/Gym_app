package com.gymtracker.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.entities.ScheduledSessionEntity
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.model.PlanExerciseDraft
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.today
import com.gymtracker.domain.timer.WorkoutTimerState
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cykl życia treningu. Najważniejsze rzeczy do pilnowania:
 *  * do sesji trafiają tylko wybory domyślne (alternatywa to podpowiedź),
 *  * cel jest **kopiowany**, więc późniejsza edycja planu nie przepisuje historii,
 *  * trwa najwyżej jeden trening naraz,
 *  * wpis w kalendarzu wiąże się z sesją (start z planu i z kalendarza),
 *  * pauza jest zapisana w bazie i nie zamienia się w czas treningu.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var plans: PlanRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl

    private val exerciseIds = mutableMapOf<String, Long>()
    private var currentTime = 10_000L
    private lateinit var timeProvider: TimeProvider

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        timeProvider = TimeProvider { currentTime }
        plans = PlanRepositoryImpl(database.planDao(), database, timeProvider)
        sessions = SessionRepositoryImpl(
            database.sessionDao(),
            database.setLogDao(),
            database.planDao(),
            database.calendarDao(),
            database,
            timeProvider,
        )

        listOf("squat" to "Barbell Back Squat", "bench" to "Barbell Bench Press")
            .forEach { (slug, name) ->
                exerciseIds[slug] = database.exerciseDao().insert(
                    ExerciseEntity(
                        externalId = slug,
                        name = name,
                        muscleGroup = "Test",
                        createdAtEpochMs = 1_000L,
                        updatedAtEpochMs = 1_000L,
                    ),
                )
            }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `start z dnia planu kopiuje cel i pomija alternatywy`() = runTest {
        val dayId = seedDay()

        val sessionId = requireNotNull(sessions.startFromPlanDay(dayId))
        val details = requireNotNull(sessions.observeSession(sessionId).first())

        assertEquals(SessionStatus.ACTIVE, details.session.status)
        assertEquals("Dzień A", details.session.name)
        assertEquals(dayId, details.session.planDayId)
        assertEquals(10_000L, details.session.startedAtEpochMs)
        assertNull(details.session.finishedAtEpochMs)

        // „Bench Press albo Military Press” - do treningu wchodzi tylko domyślne.
        assertEquals(listOf("Barbell Back Squat", "Barbell Bench Press"), details.exercises.map { it.exerciseName })
        assertEquals(listOf(1, 2), details.exercises.map { it.position })
        val bench = details.exercises[1]
        assertEquals(3, bench.targetSets)
        assertNotNull(bench.alternativeGroup)
    }

    @Test
    fun `trwa najwyzej jeden trening - nowy start zamyka poprzedni`() = runTest {
        val dayId = seedDay()
        val first = requireNotNull(sessions.startFromPlanDay(dayId))

        currentTime = 20_000L
        val second = requireNotNull(sessions.startFromPlanDay(dayId))

        val firstDetails = requireNotNull(sessions.observeSession(first).first())
        assertEquals(SessionStatus.CANCELLED, firstDetails.session.status)
        assertEquals(20_000L, firstDetails.session.finishedAtEpochMs)

        val active = requireNotNull(sessions.observeActiveSession().first())
        assertEquals(second, active.session.id)
    }

    @Test
    fun `zakonczenie treningu zapisuje czas i status`() = runTest {
        val dayId = seedDay()
        val sessionId = requireNotNull(sessions.startFromPlanDay(dayId))

        currentTime = 3_600_000L
        sessions.finishSession(sessionId)

        val details = requireNotNull(sessions.observeSession(sessionId).first())
        assertEquals(SessionStatus.FINISHED, details.session.status)
        assertEquals(3_600_000L, details.session.finishedAtEpochMs)
        assertNull("zakończony trening nie jest już aktywny", sessions.observeActiveSession().first())
    }

    @Test
    fun `usuniecie planu nie kasuje historii treningu`() = runTest {
        val (planId, dayId) = seedPlanAndDay()
        val sessionId = requireNotNull(sessions.startFromPlanDay(dayId))

        plans.deletePlan(planId)

        val details = requireNotNull(sessions.observeSession(sessionId).first())
        assertNull("wskazanie na dzień zniknęło, trening został", details.session.planDayId)
        assertTrue("ćwiczenia treningu nadal są", details.exercises.isNotEmpty())
        assertEquals(1, database.sessionDao().count())
    }

    @Test
    fun `start z dnia planu wiaze sesje z wpisem zaplanowanym na dzis`() = runTest {
        val (planId, dayId) = seedPlanAndDay()
        val today = timeProvider.today().toString()
        val scheduledId = database.calendarDao().insertScheduled(
            listOf(
                ScheduledSessionEntity(
                    date = today,
                    planId = planId,
                    planDayId = dayId,
                    title = "Dzień A",
                    status = ScheduledSessionEntity.STATUS_PLANNED,
                    createdAtEpochMs = currentTime,
                ),
            ),
        ).single()

        val sessionId = requireNotNull(sessions.startFromPlanDay(dayId))
        assertEquals(
            "wpis pamięta sesję, z której powstał",
            sessionId,
            requireNotNull(database.calendarDao().getScheduled(scheduledId)).sessionId,
        )

        // Zakończony trening odhacza wpis (odroczenie z M3 domknięte w M4).
        sessions.finishSession(sessionId)
        assertEquals(
            ScheduledSessionEntity.STATUS_DONE,
            requireNotNull(database.calendarDao().getScheduled(scheduledId)).status,
        )
    }

    @Test
    fun `trening bez wpisu w kalendarzu startuje normalnie`() = runTest {
        val dayId = seedDay()

        val sessionId = requireNotNull(sessions.startFromPlanDay(dayId))

        sessions.finishSession(sessionId)
        assertEquals(SessionStatus.FINISHED, requireNotNull(sessions.observeSession(sessionId).first()).session.status)
    }

    @Test
    fun `notatka zapisuje sie i pusta wartosc ja czysci`() = runTest {
        val sessionId = requireNotNull(sessions.startFromPlanDay(seedDay()))

        sessions.setNote(sessionId, "  plecy bolaly, nastepnym razem lzej  ")
        assertEquals(
            "notatka przycina sie z brzegow",
            "plecy bolaly, nastepnym razem lzej",
            requireNotNull(sessions.observeSession(sessionId).first()).session.note,
        )

        sessions.setNote(sessionId, "   ")
        assertNull(requireNotNull(sessions.observeSession(sessionId).first()).session.note)
    }

    @Test
    fun `pauza zapisuje sie od razu i wchodzi w sume pauz`() = runTest {
        val sessionId = requireNotNull(sessions.startFromPlanDay(seedDay()))

        currentTime = 10_000L + 60_000L
        sessions.persistTimer(
            sessionId,
            WorkoutTimerState(startedAtEpochMs = 10_000L, pausedAtEpochMs = currentTime),
        )

        val paused = requireNotNull(sessions.getActiveSession())
        assertEquals(currentTime, paused.pausedAtEpochMs)
        assertEquals(0L, paused.pausedTotalMs)
    }

    @Test
    fun `zakonczenie w trakcie pauzy dolicza ja i nie liczy do treningu`() = runTest {
        val sessionId = requireNotNull(sessions.startFromPlanDay(seedDay()))

        // Zegar idzie 10 minut, potem pauza na 5 minut.
        val pausedAt = 10_000L + 10 * 60_000L
        sessions.persistTimer(
            sessionId,
            WorkoutTimerState(startedAtEpochMs = 10_000L, pausedTotalMs = 0L, pausedAtEpochMs = pausedAt),
        )
        currentTime = pausedAt + 5 * 60_000L
        sessions.finishSession(sessionId)

        val finished = requireNotNull(sessions.observeSession(sessionId).first()).session
        assertNull("zamkniety trening nie ma otwartej pauzy", finished.pausedAtEpochMs)
        assertEquals(5 * 60_000L, finished.pausedTotalMs)
        assertEquals("czas treningu to 10 minut, nie 15", 10 * 60_000L, finished.durationMs)
    }

    @Test
    fun `nowy start zamyka poprzedni trening razem z otwarta pauza`() = runTest {
        val dayId = seedDay()
        val first = requireNotNull(sessions.startFromPlanDay(dayId))
        sessions.persistTimer(
            first,
            WorkoutTimerState(startedAtEpochMs = 10_000L, pausedAtEpochMs = 40_000L),
        )

        currentTime = 100_000L
        val second = requireNotNull(sessions.startFromPlanDay(dayId))

        val cancelled = requireNotNull(sessions.observeSession(first).first()).session
        assertEquals(SessionStatus.CANCELLED, cancelled.status)
        assertNull(cancelled.pausedAtEpochMs)
        assertEquals(60_000L, cancelled.pausedTotalMs)
        assertEquals(second, requireNotNull(sessions.getActiveSession()).id)
    }

    @Test
    fun `start z wpisu w kalendarzu nalezy do dnia wpisu`() = runTest {
        val (planId, dayId) = seedPlanAndDay()
        val date = timeProvider.today().plusDays(9)
        val scheduledId = seedScheduled(date = date, planId = planId, dayId = dayId)

        val sessionId = requireNotNull(sessions.startFromSchedule(scheduledId))
        val session = requireNotNull(sessions.observeSession(sessionId).first()).session

        assertEquals("dzień wpisu, nie dzień kliknięcia", date, session.sessionDate)
        assertEquals(dayId, session.planDayId)
        assertEquals(
            "wpis pamięta sesję, z której powstał",
            sessionId,
            requireNotNull(database.calendarDao().getScheduled(scheduledId)).sessionId,
        )
    }

    @Test
    fun `start na wybrany dzien przypisuje trening do tego dnia i wiaze wpis`() = runTest {
        val (planId, dayId) = seedPlanAndDay()
        val date = timeProvider.today().plusDays(3)
        val scheduledId = seedScheduled(date = date, planId = planId, dayId = dayId)

        val sessionId = requireNotNull(sessions.startForDay(date = date, planDayId = dayId, name = "Trening"))
        val details = requireNotNull(sessions.observeSession(sessionId).first())

        assertEquals(date, details.session.sessionDate)
        assertEquals("nazwa idzie z dnia planu", "Dzień A", details.session.name)
        assertEquals(
            "ćwiczenia dnia planu trafiają do treningu",
            listOf("Barbell Back Squat", "Barbell Bench Press"),
            details.exercises.map { it.exerciseName },
        )
        assertEquals(
            sessionId,
            requireNotNull(database.calendarDao().getScheduled(scheduledId)).sessionId,
        )

        sessions.finishSession(sessionId)
        assertEquals(
            ScheduledSessionEntity.STATUS_DONE,
            requireNotNull(database.calendarDao().getScheduled(scheduledId)).status,
        )
    }

    @Test
    fun `start na dzien bez planu tworzy pusty trening z podana nazwa`() = runTest {
        val date = timeProvider.today().plusDays(2)

        val sessionId = requireNotNull(sessions.startForDay(date = date, planDayId = null, name = "Trening"))
        val details = requireNotNull(sessions.observeSession(sessionId).first())

        assertEquals(date, details.session.sessionDate)
        assertEquals("Trening", details.session.name)
        assertNull(details.session.planDayId)
        assertTrue("trening od zera startuje bez ćwiczeń", details.exercises.isEmpty())
    }

    @Test
    fun `dopisanie cwiczenia do trwajacego treningu ustawia je na koncu`() = runTest {
        val dayId = seedDay()
        val sessionId = requireNotNull(sessions.startFromPlanDay(dayId))
        val extraId = requireNotNull(exerciseIds["squat"])

        assertTrue(sessions.addExerciseToSession(sessionId, extraId))

        val exercises = requireNotNull(sessions.observeSession(sessionId).first()).exercises
        assertEquals(3, exercises.size)
        assertEquals(listOf(1, 2, 3), exercises.map { it.position })
    }

    @Test
    fun `dopisanie cwiczenia do zakonczonego treningu nic nie zmienia`() = runTest {
        val sessionId = requireNotNull(sessions.startFromPlanDay(seedDay()))
        sessions.finishSession(sessionId)

        assertFalse(sessions.addExerciseToSession(sessionId, requireNotNull(exerciseIds["squat"])))
        assertEquals(
            2,
            requireNotNull(sessions.observeSession(sessionId).first()).exercises.size,
        )
    }

    @Test
    fun `treningi z zakresu dni licza dzien przypisany i dzien startu`() = runTest {
        val (planId, dayId) = seedPlanAndDay()
        val today = timeProvider.today()
        val assigned = today.plusDays(5)
        val scheduledId = seedScheduled(date = assigned, planId = planId, dayId = dayId)
        val assignedSession = requireNotNull(sessions.startFromSchedule(scheduledId))
        sessions.finishSession(assignedSession)

        // Trening „od zera” nie ma przypisanej daty - liczy się dzień startu.
        val free = requireNotNull(sessions.startForDay(today, planDayId = null, name = "Trening"))
        sessions.finishSession(free)

        val onAssigned = sessions.observeWorkoutsBetween(assigned, assigned).first()
        assertEquals(listOf(assignedSession), onAssigned.map { workout -> workout.sessionId })
        assertEquals(assigned, onAssigned.single().date)
        assertEquals("Dzień A", onAssigned.single().name)
        assertTrue("trening jest już odbyty", onAssigned.single().isFinished)

        val onStartDay = sessions.observeWorkoutsBetween(today, today).first()
        assertEquals(listOf(free), onStartDay.map { workout -> workout.sessionId })
        assertEquals(today, onStartDay.single().date)
    }

    @Test
    fun `przerwany trening nie liczy sie jako dzien treningu`() = runTest {
        val today = timeProvider.today()
        val sessionId = requireNotNull(sessions.startForDay(today, planDayId = null, name = "Trening"))
        sessions.cancelSession(sessionId)

        assertTrue(sessions.observeWorkoutsBetween(today, today).first().isEmpty())
    }

    @Test
    fun `poprawka dnia przenosi wpis kalendarza na nowy dzien`() = runTest {
        val (planId, dayId) = seedPlanAndDay()
        val today = timeProvider.today()
        val scheduledId = seedScheduled(date = today, planId = planId, dayId = dayId)
        val sessionId = requireNotNull(sessions.startForDay(today, planDayId = dayId, name = "Trening"))
        sessions.finishSession(sessionId)

        val fixed = today.minusDays(2)
        sessions.setSessionDate(sessionId, fixed)

        assertEquals(
            fixed,
            requireNotNull(sessions.observeSession(sessionId).first()).session.sessionDate,
        )
        val entry = requireNotNull(database.calendarDao().getScheduled(scheduledId))
        assertEquals("wpis idzie za treningiem", fixed.toString(), entry.date)
        assertEquals(
            "wpis zostaje odhaczony",
            ScheduledSessionEntity.STATUS_DONE,
            entry.status,
        )
    }

    @Test
    fun `poprawka dnia na zajety dzien odczepia wpis od treningu`() = runTest {
        val (planId, dayId) = seedPlanAndDay()
        val today = timeProvider.today()
        val doneId = seedScheduled(date = today, planId = planId, dayId = dayId)
        val sessionId = requireNotNull(sessions.startForDay(today, planDayId = dayId, name = "Trening"))
        sessions.finishSession(sessionId)

        // Na nowy dzień ten sam dzień planu jest już zaplanowany.
        val target = today.minusDays(1)
        val plannedId = seedScheduled(date = target, planId = planId, dayId = dayId)

        sessions.setSessionDate(sessionId, target)

        val done = requireNotNull(database.calendarDao().getScheduled(doneId))
        assertNull("wpis nie jest już przypisany do treningu", done.sessionId)
        assertEquals(
            "wpis nie udaje odbytego dnia",
            ScheduledSessionEntity.STATUS_PLANNED,
            done.status,
        )
        val planned = requireNotNull(database.calendarDao().getScheduled(plannedId))
        assertEquals("wpis na nowym dniu zostaje bez zmian", target.toString(), planned.date)
        assertEquals(ScheduledSessionEntity.STATUS_PLANNED, planned.status)
        assertEquals(
            target,
            requireNotNull(sessions.observeSession(sessionId).first()).session.sessionDate,
        )
    }

    @Test
    fun `poprawka dnia trwajacego treningu nic nie zmienia`() = runTest {
        val today = timeProvider.today()
        val sessionId = requireNotNull(sessions.startForDay(today, planDayId = null, name = "Trening"))

        sessions.setSessionDate(sessionId, today.minusDays(3))

        assertEquals(
            "dzień trwającego treningu wybiera się przy starcie",
            today,
            requireNotNull(sessions.observeSession(sessionId).first()).session.sessionDate,
        )
    }

    /** Plan z jednym dniem: przysiad 5×5, wyciskanie 3×10 albo wojskowe. */
    private suspend fun seedDay(): Long = seedPlanAndDay().second

    private suspend fun seedScheduled(
        date: LocalDate,
        planId: Long,
        dayId: Long,
    ): Long = database.calendarDao().insertScheduled(
        listOf(
            ScheduledSessionEntity(
                date = date.toString(),
                planId = planId,
                planDayId = dayId,
                title = "Dzień A",
                status = ScheduledSessionEntity.STATUS_PLANNED,
                createdAtEpochMs = currentTime,
            ),
        ),
    ).single()

    private suspend fun seedPlanAndDay(): Pair<Long, Long> {
        val planId = plans.createPlan(PlanDraft(name = "Plan"))
        val dayId = plans.addDay(planId, PlanDayDraft(name = "Dzień A"))
        plans.addExercise(
            dayId,
            PlanExerciseDraft(
                exerciseId = requireNotNull(exerciseIds["squat"]),
                sets = 5,
                repsMin = 5,
                repsMax = 5,
                repsUnit = RepsUnit.REPS,
                restSeconds = 120,
            ),
        )
        val bench = plans.addExercise(
            dayId,
            PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["bench"]), sets = 3, repsMin = 10, repsMax = 10),
        )
        plans.addAlternative(bench, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["squat"])))
        return planId to dayId
    }
}
