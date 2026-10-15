package com.gymtracker.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.model.PlanExerciseDraft
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.time.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Start treningu z planu. Najważniejsze rzeczy do pilnowania:
 *  * do sesji trafiają tylko wybory domyślne (alternatywa to podpowiedź),
 *  * cel jest **kopiowany**, więc późniejsza edycja planu nie przepisuje historii,
 *  * trwa najwyżej jeden trening naraz.
 */
@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {

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
        sessions = SessionRepositoryImpl(database.sessionDao(), database.planDao(), database, timeProvider)

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
        assertEquals(3, bench.sets)
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

    /** Plan z jednym dniem: przysiad 5×5, wyciskanie 3×10 albo wojskowe. */
    private suspend fun seedDay(): Long = seedPlanAndDay().second

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
