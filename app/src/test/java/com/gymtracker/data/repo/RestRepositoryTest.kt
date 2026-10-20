package com.gymtracker.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.model.PlanExerciseDraft
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.RestEndReason
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.time.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Przerwy między seriami. Najważniejsze, co musi się zgadzać:
 *
 *  * start przerwy od razu trafia do bazy (przerwa przeżyje ubicie aplikacji),
 *  * czas przerwy liczy zegar, nie ekran,
 *  * domknięcie jest idempotentne - alarm, ekran i nowa seria mogą się ścigać,
 *  * do wiersza serii trafia tylko przerwa, która coś mówi o formie,
 *  * statystyki liczą się per ćwiczenie i odsiewają pominięte przerwy.
 */
@RunWith(RobolectricTestRunner::class)
class RestRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var plans: PlanRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl
    private lateinit var rests: RestRepositoryImpl

    private val exerciseIds = mutableMapOf<String, Long>()
    private var currentTime = 10_000L

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        val timeProvider = TimeProvider { currentTime }
        plans = PlanRepositoryImpl(database.planDao(), database, timeProvider)
        sessions = SessionRepositoryImpl(
            database.sessionDao(),
            database.setLogDao(),
            database.planDao(),
            database.calendarDao(),
            database.exerciseDao(),
            database,
            timeProvider,
        )
        rests = RestRepositoryImpl(
            database.restDao(),
            database.setLogDao(),
            database.exerciseDao(),
            database,
            timeProvider,
        )

        exerciseIds["squat"] = database.exerciseDao().insert(
            ExerciseEntity(
                externalId = "squat",
                name = "Przysiad",
                muscleGroup = "Nogi",
                defaultRestS = 120,
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `start przerwy zapisuje otwarty wiersz z czasem z zegara`() = runTest {
        val (sessionId, sessionExerciseId, setLogId) = startedSet()

        val interval = rests.start(
            exerciseId = requireNotNull(exerciseIds["squat"]),
            sessionId = sessionId,
            setLogId = setLogId,
            plannedSeconds = 90,
        )

        assertEquals(currentTime, interval.startedAtEpochMs)
        assertEquals(90, interval.plannedSeconds)
        assertTrue(interval.isRunning)
        assertNull("otwarta przerwa nie ma powodu zakończenia", interval.reason)
        assertEquals(interval.id, requireNotNull(rests.getOpen()).id)
        assertEquals(sessionExerciseId, requireNotNull(database.setLogDao().getSet(setLogId)).sessionExerciseId)
    }

    @Test
    fun `domkniecie liczy faktyczny czas i przepisuje go do serii`() = runTest {
        val (sessionId, _, setLogId) = startedSet()
        val interval = rests.start(
            exerciseId = requireNotNull(exerciseIds["squat"]),
            sessionId = sessionId,
            setLogId = setLogId,
            plannedSeconds = 90,
        )

        currentTime += 94_000L
        assertTrue(rests.close(interval.id, RestEndReason.COMPLETED, plannedSeconds = 90))

        val closed = requireNotNull(rests.get(interval.id))
        assertFalse(closed.isRunning)
        assertEquals(94, closed.actualSeconds)
        assertEquals(RestEndReason.COMPLETED, closed.reason)
        assertEquals(currentTime, closed.endedAtEpochMs)

        val set = requireNotNull(database.setLogDao().getSet(setLogId))
        assertEquals(94, set.restAfterS)
        assertEquals(90, set.restTargetS)
    }

    @Test
    fun `drugie domkniecie nic nie zmienia`() = runTest {
        val (sessionId, _, setLogId) = startedSet()
        val interval = rests.start(
            exerciseId = requireNotNull(exerciseIds["squat"]),
            sessionId = sessionId,
            setLogId = setLogId,
            plannedSeconds = 90,
        )
        currentTime += 95_000L
        rests.close(interval.id, RestEndReason.COMPLETED, plannedSeconds = 90)

        currentTime += 60_000L
        assertFalse(
            "alarm po fakcie nie może przepisać zamkniętej przerwy",
            rests.close(interval.id, RestEndReason.SKIPPED, plannedSeconds = 90),
        )

        val closed = requireNotNull(rests.get(interval.id))
        assertEquals(95, closed.actualSeconds)
        assertEquals(RestEndReason.COMPLETED, closed.reason)
        assertEquals(95, requireNotNull(database.setLogDao().getSet(setLogId)).restAfterS)
    }

    @Test
    fun `pominieta przerwa nie zapisuje sie w wierszu serii`() = runTest {
        val (sessionId, _, setLogId) = startedSet()
        val interval = rests.start(
            exerciseId = requireNotNull(exerciseIds["squat"]),
            sessionId = sessionId,
            setLogId = setLogId,
            plannedSeconds = 120,
        )

        currentTime += 4_000L
        rests.close(interval.id, RestEndReason.SKIPPED, plannedSeconds = 120)

        val set = requireNotNull(database.setLogDao().getSet(setLogId))
        assertNull("pominięta przerwa nie jest przerwą po tej serii", set.restAfterS)
        assertNull(set.restTargetS)
        assertEquals(RestEndReason.SKIPPED, requireNotNull(rests.get(interval.id)).reason)
    }

    @Test
    fun `korekta celu trafia do bazy`() = runTest {
        val (sessionId, _, setLogId) = startedSet()
        val interval = rests.start(
            exerciseId = requireNotNull(exerciseIds["squat"]),
            sessionId = sessionId,
            setLogId = setLogId,
            plannedSeconds = 60,
        )

        rests.setPlannedSeconds(interval.id, 75)

        assertEquals(75, requireNotNull(rests.get(interval.id)).plannedSeconds)
        assertTrue("domknięta przerwa nie przyjmuje korekt", rests.close(interval.id, RestEndReason.EXTENDED, 75))
    }

    @Test
    fun `statystyki licza sie per cwiczenie i odsiewaja pomineiete`() = runTest {
        val squatId = requireNotNull(exerciseIds["squat"])
        val (sessionId, _, setLogId) = startedSet()
        val second = database.exerciseDao().insert(
            ExerciseEntity(
                externalId = "bench",
                name = "Wyciskanie",
                muscleGroup = "Klatka",
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L,
            ),
        )

        // Przysiad: dwie przerwy do średniej (60 s i 120 s).
        val first = rests.start(squatId, sessionId, setLogId, plannedSeconds = 60)
        currentTime += 60_000L
        rests.close(first.id, RestEndReason.COMPLETED, 60)
        val second1 = rests.start(squatId, sessionId, setLogId, plannedSeconds = 120)
        currentTime += 120_000L
        rests.close(second1.id, RestEndReason.EXTENDED, 120)
        // …i jedna pominięta, która nie może zaniżyć średniej.
        val skipped = rests.start(squatId, sessionId, setLogId, plannedSeconds = 30)
        currentTime += 3_000L
        rests.close(skipped.id, RestEndReason.SKIPPED, 30)

        // Inne ćwiczenie ma własną średnią.
        val other = rests.start(second, sessionId, setLogId, plannedSeconds = 45)
        currentTime += 45_000L
        rests.close(other.id, RestEndReason.COMPLETED, 45)

        val stats = rests.observeStats(listOf(squatId, second)).first()

        assertEquals(2, stats.getValue(squatId).samples)
        assertEquals(90, stats.getValue(squatId).averageSeconds)
        assertEquals(1, stats.getValue(second).samples)
        assertEquals(45, stats.getValue(second).averageSeconds)
    }

    @Test
    fun `podglad sesji pokazuje wszystkie przerwy takze pomieniete`() = runTest {
        val (sessionId, _, setLogId) = startedSet()
        val completed = rests.start(requireNotNull(exerciseIds["squat"]), sessionId, setLogId, 60)
        currentTime += 60_000L
        rests.close(completed.id, RestEndReason.COMPLETED, 60)
        val skipped = rests.start(requireNotNull(exerciseIds["squat"]), sessionId, setLogId, 30)
        currentTime += 2_000L
        rests.close(skipped.id, RestEndReason.SKIPPED, 30)

        val sessionRests = rests.observeSessionRests(sessionId).first()

        assertEquals(2, sessionRests.size)
        assertEquals(62_000L, sessionRests.sumOf { it.elapsedMs(currentTime) })
    }

    @Test
    fun `nazwa cwiczenia jest czytelna takze po usunieciu z katalogu`() = runTest {
        val squatId = requireNotNull(exerciseIds["squat"])
        assertEquals("Przysiad", rests.exerciseName(squatId))

        database.exerciseDao().archive(squatId, updatedAtEpochMs = 2_000L)

        assertEquals("Przysiad", rests.exerciseName(squatId))
    }

    @Test
    fun `cel przerwy dla pozycji treningu da sie poprawic na dzisiaj`() = runTest {
        val (_, sessionExerciseId, _) = startedSet()

        sessions.setExerciseRest(sessionExerciseId, 45)
        assertEquals(45, exerciseInSession(sessionExerciseId).restSeconds)

        sessions.setExerciseRest(sessionExerciseId, 0)
        assertNull("zero znaczy „bez przerwy”, a nie przerwa zerowa", exerciseInSession(sessionExerciseId).restSeconds)
    }

    /** Trening z jednym ćwiczeniem i jedną zalogowaną serią. */
    private suspend fun startedSet(): Triple<Long, Long, Long> {
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
            ),
        )
        val sessionId = requireNotNull(sessions.startFromPlanDay(dayId))
        val exercise = exerciseInSession(requireNotNull(sessionIdExerciseId(sessionId)))
        val setLogId = requireNotNull(sessions.addSet(exercise.id, SetLogDraft(weightKg = 60.0, reps = 5)))
        return Triple(sessionId, exercise.id, setLogId)
    }

    private suspend fun sessionIdExerciseId(sessionId: Long): Long? =
        sessions.observeSession(sessionId).first()?.exercises?.single()?.id

    /** Ćwiczenie treningu z doczepionymi seriami i celem przerwy. */
    private suspend fun exerciseInSession(sessionExerciseId: Long): SessionExercise {
        val sessionId = requireNotNull(database.sessionDao().getSessionExercise(sessionExerciseId)).sessionId
        return requireNotNull(sessions.observeSession(sessionId).first())
            .exercises
            .first { exercise -> exercise.id == sessionExerciseId }
    }
}
