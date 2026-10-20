package com.gymtracker.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.model.PlanExerciseDraft
import com.gymtracker.domain.model.RepsUnit
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
 * Logowanie serii: najważniejsze, co musi się zgadzać.
 *
 *  * numeracja jest ciągła (1, 2, 3…) nawet po usunięciu serii ze środka,
 *  * seria na czas zapisuje `duration_s`, a na powtórzenia `reps`,
 *  * serie, które nic nie znaczą (brak wartości), nie trafiają do bazy,
 *  * historia jest zamknięta: do zakończonego treningu nic już nie dopiszemy.
 */
@RunWith(RobolectricTestRunner::class)
class SetLogRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var plans: PlanRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl

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

        listOf("squat" to "Przysiad", "plank" to "Plank")
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
    fun `serie dostaja kolejne numery i sume ciezarow`() = runTest {
        val sessionId = startSession()
        val squat = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }

        sessions.addSet(squat.id, SetLogDraft(weightKg = 60.0, reps = 5))
        currentTime += 180_000L
        sessions.addSet(squat.id, SetLogDraft(weightKg = 80.0, reps = 5))
        currentTime += 180_000L
        sessions.addSet(squat.id, SetLogDraft(weightKg = 100.0, reps = 3, isWarmup = true))

        val logs = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }.logs
        assertEquals(listOf(1, 2, 3), logs.map { it.setIndex })
        assertEquals(listOf(60.0, 80.0, 100.0), logs.map { it.weightKg })
        assertEquals(listOf(5, 5, 3), logs.map { it.reps })
        assertTrue("trzecia seria to rozgrzewka", logs.last().isWarmup)
    }

    @Test
    fun `seria na czas zapisuje sekundy zamiast powtorzen`() = runTest {
        val sessionId = startSession()
        val plank = exerciseLogs(sessionId).first { it.exerciseName == "Plank" }

        sessions.addSet(plank.id, SetLogDraft(durationS = 60))

        val log = exerciseLogs(sessionId).first { it.exerciseName == "Plank" }.logs.single()
        assertEquals(60, log.durationS)
        assertNull("seria na czas nie ma powtorzen", log.reps)
        assertNull("plank nie ma ciezarow", log.weightKg)
    }

    @Test
    fun `seria bez wartosci nie trafia do bazy`() = runTest {
        val sessionId = startSession()
        val squat = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }
        val plank = exerciseLogs(sessionId).first { it.exerciseName == "Plank" }

        // Przysiad oczekuje powtórzeń, plank czasu - same ciężary nie wystarczą.
        assertNull(sessions.addSet(squat.id, SetLogDraft(weightKg = 60.0)))
        assertNull(sessions.addSet(plank.id, SetLogDraft(weightKg = 10.0, reps = 5)))
        assertTrue(exerciseLogs(sessionId).all { it.logs.isEmpty() })
    }

    @Test
    fun `poprawa serii nie zmienia numeru ani czasu zapisu`() = runTest {
        val sessionId = startSession()
        val squat = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }
        sessions.addSet(squat.id, SetLogDraft(weightKg = 60.0, reps = 5))
        val original = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }.logs.single()

        currentTime += 60_000L
        assertTrue(sessions.updateSet(original.id, SetLogDraft(weightKg = 62.5, reps = 6)))

        val updated = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }.logs.single()
        assertEquals(1, updated.setIndex)
        assertEquals(original.completedAtEpochMs, updated.completedAtEpochMs)
        assertEquals(62.5, updated.weightKg!!, 0.001)
        assertEquals(6, updated.reps)
    }

    @Test
    fun `usuniecie serii przenumerowuje pozostale`() = runTest {
        val sessionId = startSession()
        val squat = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }
        listOf(60.0, 80.0, 100.0).forEach { weight ->
            sessions.addSet(squat.id, SetLogDraft(weightKg = weight, reps = 5))
        }
        val middle = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }.logs[1]

        sessions.deleteSet(middle.id)

        val left = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }.logs
        assertEquals(listOf(1, 2), left.map { it.setIndex })
        assertEquals("dziura po usunietej serii zostaje zamknieta", listOf(60.0, 100.0), left.map { it.weightKg })
    }

    @Test
    fun `do zakonczonego treningu nie dopiszemy serii`() = runTest {
        val sessionId = startSession()
        val squat = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }
        sessions.addSet(squat.id, SetLogDraft(reps = 5))
        val log = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }.logs.single()

        sessions.finishSession(sessionId)

        assertNull(sessions.addSet(squat.id, SetLogDraft(reps = 5)))
        assertFalse(sessions.updateSet(log.id, SetLogDraft(reps = 8)))
        assertTrue("stara seria zostaje jak byla", exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }.logs.isNotEmpty())
    }

    @Test
    fun `serie sa czescia cwiczenia w podgladzie treningu`() = runTest {
        val sessionId = startSession()
        val squat = exerciseLogs(sessionId).first { it.exerciseName == "Przysiad" }
        sessions.addSet(squat.id, SetLogDraft(weightKg = 60.0, reps = 5))

        val details = requireNotNull(sessions.observeSession(sessionId).first())
        val bench = details.exercises.first { it.exerciseName == "Przysiad" }
        assertEquals(1, bench.logs.size)
        assertEquals(60.0, bench.logs.single().weightKg)
        assertEquals("cel z planu zostaje przy cwiczeniu", 5, bench.targetSets)
    }

    /** Trening z dnia planu: przysiad 5×5 i plank 3×60 s. */
    private suspend fun startSession(): Long {
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
        plans.addExercise(
            dayId,
            PlanExerciseDraft(
                exerciseId = requireNotNull(exerciseIds["plank"]),
                sets = 3,
                repsMin = 60,
                repsMax = 60,
                repsUnit = RepsUnit.SECONDS,
            ),
        )
        return requireNotNull(sessions.startFromPlanDay(dayId))
    }

    private suspend fun exerciseLogs(sessionId: Long) =
        requireNotNull(sessions.observeSession(sessionId).first()).exercises
}
