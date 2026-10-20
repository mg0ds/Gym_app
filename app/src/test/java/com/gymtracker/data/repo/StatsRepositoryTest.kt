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
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.domain.time.TimeProvider
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Historia treningów i statystyki ćwiczenia na prawdziwej bazie.
 *
 * Tu sprawdzamy to, czego nie widzi czysta arytmetyka: które sesje wchodzą do
 * historii (status), czy SQL dobrze sumuje serie robocze i czy filtr po
 * ćwiczeniu faktycznie wie, co było na treningu. Strefę przypinamy do UTC,
 * żeby „dzień treningu” nie zależał od strefy komputera, na którym biegnie test.
 */
@RunWith(RobolectricTestRunner::class)
class StatsRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var plans: PlanRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl
    private lateinit var rests: RestRepositoryImpl
    private lateinit var stats: StatsRepositoryImpl

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 10, 10)
    private val exerciseIds = mutableMapOf<String, Long>()
    private var currentTime = 0L
    private lateinit var previousZone: TimeZone

    @Before
    fun setUp() = runTest {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

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
        stats = StatsRepositoryImpl(
            database.statsDao(),
            database.sessionDao(),
            database.restDao(),
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
        exerciseIds["bench"] = database.exerciseDao().insert(
            ExerciseEntity(
                externalId = "bench",
                name = "Wyciskanie",
                muscleGroup = "Klatka",
                defaultRestS = 90,
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L,
            ),
        )
        currentTime = moment(daysAgo = 0)
    }

    @After
    fun tearDown() {
        database.close()
        TimeZone.setDefault(previousZone)
    }

    /** Moment o 12:00 UTC danego dnia - żeby data treningu nie zależała od strefy. */
    private fun moment(daysAgo: Long): Long =
        today.minusDays(daysAgo).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    /** Dzień planu z podanymi ćwiczeniami (w kolejności). */
    private suspend fun planDay(vararg keys: String): Long {
        val planId = plans.createPlan(PlanDraft(name = "Plan"))
        val dayId = plans.addDay(planId, PlanDayDraft(name = "Dzień A"))
        keys.forEach { key ->
            plans.addExercise(
                dayId,
                PlanExerciseDraft(
                    exerciseId = requireNotNull(exerciseIds[key]),
                    sets = 3,
                    repsMin = 5,
                    repsMax = 5,
                    repsUnit = RepsUnit.REPS,
                ),
            )
        }
        return dayId
    }

    private suspend fun startSession(dayId: Long, daysAgo: Long): Long {
        currentTime = moment(daysAgo)
        return requireNotNull(sessions.startFromPlanDay(dayId))
    }

    private suspend fun exerciseInSession(sessionId: Long, index: Int = 0): Long =
        requireNotNull(sessions.observeSession(sessionId).first()).exercises[index].id

    private suspend fun logSet(sessionExerciseId: Long, draft: SetLogDraft): Long =
        requireNotNull(sessions.addSet(sessionExerciseId, draft))

    private suspend fun finish(sessionId: Long, daysAgo: Long) {
        currentTime = moment(daysAgo) + 3_600_000L
        sessions.finishSession(sessionId)
    }

    /** Przerwa po serii: start i domknięcie po [seconds]. */
    private suspend fun restAfter(
        exerciseId: Long,
        sessionId: Long,
        setLogId: Long,
        seconds: Int,
        reason: RestEndReason,
    ) {
        val interval = rests.start(exerciseId, sessionId, setLogId, plannedSeconds = 90)
        currentTime += seconds * 1_000L
        rests.close(interval.id, reason, plannedSeconds = 90)
    }

    @Test
    fun `historia pokazuje zakonczone treningi od najnowszych`() = runTest {
        val dayId = planDay("squat")
        val older = startSession(dayId, daysAgo = 9)
        logSet(exerciseInSession(older), SetLogDraft(weightKg = 100.0, reps = 5))
        finish(older, daysAgo = 9)

        val newer = startSession(dayId, daysAgo = 1)
        logSet(exerciseInSession(newer), SetLogDraft(weightKg = 105.0, reps = 5))
        finish(newer, daysAgo = 1)

        val summaries = stats.observeSessionSummaries().first()

        assertEquals(listOf(newer, older), summaries.map { summary -> summary.sessionId })
        assertEquals(today.minusDays(1), summaries.first().date)
        assertEquals("Dzień A", summaries.first().name)
        assertEquals(3_600_000L, summaries.first().durationMs)
    }

    @Test
    fun `trening w toku nie trafia do historii`() = runTest {
        val dayId = planDay("squat")
        val sessionId = startSession(dayId, daysAgo = 0)
        logSet(exerciseInSession(sessionId), SetLogDraft(weightKg = 100.0, reps = 5))

        assertTrue(stats.observeSessionSummaries().first().isEmpty())
    }

    @Test
    fun `agregat treningu liczy tylko serie robocze`() = runTest {
        val dayId = planDay("squat")
        val sessionId = startSession(dayId, daysAgo = 0)
        val exerciseId = exerciseInSession(sessionId)

        logSet(exerciseId, SetLogDraft(weightKg = 40.0, reps = 10, isWarmup = true))
        logSet(exerciseId, SetLogDraft(weightKg = 100.0, reps = 5))
        logSet(exerciseId, SetLogDraft(weightKg = 100.0, reps = 5))
        finish(sessionId, daysAgo = 0)

        val summary = stats.observeSessionSummaries().first().single()

        assertEquals("rozgrzewka to nie seria robocza", 2, summary.workingSets)
        assertEquals(1_000.0, summary.volumeKg, 0.001)
        assertEquals(10, summary.totalReps)
        assertEquals(100.0, requireNotNull(summary.bestWeightKg), 0.001)
    }

    @Test
    fun `srednia przerwa treningu liczy tylko odbyte przerwy`() = runTest {
        val dayId = planDay("squat")
        val sessionId = startSession(dayId, daysAgo = 0)
        val exerciseId = exerciseInSession(sessionId)
        val squatId = requireNotNull(exerciseIds["squat"])

        val firstSet = logSet(exerciseId, SetLogDraft(weightKg = 100.0, reps = 5))
        restAfter(squatId, sessionId, firstSet, seconds = 60, reason = RestEndReason.COMPLETED)
        val secondSet = logSet(exerciseId, SetLogDraft(weightKg = 100.0, reps = 5))
        restAfter(squatId, sessionId, secondSet, seconds = 120, reason = RestEndReason.EXTENDED)
        val thirdSet = logSet(exerciseId, SetLogDraft(weightKg = 100.0, reps = 5))
        restAfter(squatId, sessionId, thirdSet, seconds = 3, reason = RestEndReason.SKIPPED)
        finish(sessionId, daysAgo = 0)

        val summary = stats.observeSessionSummaries().first().single()

        assertEquals("(60 + 120) / 2, bez pominiętej", 90, summary.restAverageSeconds)
    }

    @Test
    fun `filtr po cwiczeniu wie, co bylo na treningu`() = runTest {
        val squatDay = planDay("squat")
        val benchDay = planDay("bench")
        val squatSession = startSession(squatDay, daysAgo = 3)
        logSet(exerciseInSession(squatSession), SetLogDraft(weightKg = 100.0, reps = 5))
        finish(squatSession, daysAgo = 3)
        val benchSession = startSession(benchDay, daysAgo = 1)
        logSet(exerciseInSession(benchSession), SetLogDraft(weightKg = 60.0, reps = 5))
        finish(benchSession, daysAgo = 1)

        val summaries = stats.observeSessionSummaries().first()
        val benchId = requireNotNull(exerciseIds["bench"])

        assertEquals(
            listOf(benchSession),
            summaries.filter { summary -> benchId in summary.exerciseIds }.map { it.sessionId },
        )
    }

    @Test
    fun `statystyki cwiczenia lacza treningi i licza rekordy`() = runTest {
        val dayId = planDay("squat")
        val squatId = requireNotNull(exerciseIds["squat"])

        val older = startSession(dayId, daysAgo = 20)
        val olderExercise = exerciseInSession(older)
        logSet(olderExercise, SetLogDraft(weightKg = 100.0, reps = 5))
        logSet(olderExercise, SetLogDraft(weightKg = 90.0, reps = 10))
        finish(older, daysAgo = 20)

        val newer = startSession(dayId, daysAgo = 2)
        val newerExercise = exerciseInSession(newer)
        logSet(newerExercise, SetLogDraft(weightKg = 105.0, reps = 3))
        finish(newer, daysAgo = 2)

        val result = stats.observeExerciseStats(squatId, StatsRange.ALL).first()

        assertEquals(2, result.points.size)
        assertEquals(today.minusDays(20), result.points.first().date)
        assertEquals("100 kg × 5 + 90 kg × 10", 1_400.0, result.points.first().volumeKg, 0.001)
        assertEquals(
            "2 treningi, 3 serie robocze",
            2,
            result.sessions,
        )
        assertEquals(3, result.sets)
        assertEquals(1_715.0, result.volumeKg, 0.001)
        assertEquals("najcięższa seria", 105.0, requireNotNull(result.records.heaviestSet).weightKg, 0.001)
        assertEquals("1RM z 90 kg × 10", 120.0, requireNotNull(result.records.bestOneRepMax).estimatedOneRepMaxKg!!, 0.01)
        assertEquals(1_400.0, requireNotNull(result.records.bestVolume).volumeKg, 0.001)
    }

    @Test
    fun `zakres zawęża wykres, ale rekord zostaje z calej historii`() = runTest {
        val dayId = planDay("squat")
        val squatId = requireNotNull(exerciseIds["squat"])

        val old = startSession(dayId, daysAgo = 200)
        logSet(exerciseInSession(old), SetLogDraft(weightKg = 140.0, reps = 3))
        finish(old, daysAgo = 200)

        val recent = startSession(dayId, daysAgo = 3)
        logSet(exerciseInSession(recent), SetLogDraft(weightKg = 100.0, reps = 5))
        finish(recent, daysAgo = 3)

        val result = stats.observeExerciseStats(squatId, StatsRange.MONTH_3).first()

        assertEquals("wykres tylko z zakresu", 1, result.points.size)
        assertEquals(today.minusDays(3), result.points.single().date)
        assertEquals("rekord sprzed pół roku nadal jest rekordem", 140.0, requireNotNull(result.records.heaviestSet).weightKg, 0.001)
        assertEquals(1, result.sessions)
        assertEquals(500.0, result.volumeKg, 0.001)
    }

    @Test
    fun `trening z wpisu na przyszlosc liczy sie do dnia wpisu`() = runTest {
        val dayId = planDay("squat")
        val squatId = requireNotNull(exerciseIds["squat"])
        val plannedDay = today.plusDays(5)

        val sessionId = requireNotNull(
            sessions.startForDay(date = plannedDay, planDayId = dayId, name = "Trening"),
        )
        logSet(exerciseInSession(sessionId), SetLogDraft(weightKg = 100.0, reps = 5))
        currentTime = moment(daysAgo = 0) + 3_600_000L
        sessions.finishSession(sessionId)

        val summary = stats.observeSessionSummaries().first().single()
        assertEquals("historia pokazuje dzień treningu, nie dzień kliknięcia", plannedDay, summary.date)

        val result = stats.observeExerciseStats(squatId, StatsRange.MONTH_1).first()
        assertEquals(
            "trening przypisany do przyszłego dnia nie znika z wykresu",
            listOf(plannedDay),
            result.points.map { point -> point.date },
        )
        assertEquals(1, result.sessions)
    }

    @Test
    fun `statystyki cwiczenia bez historii sa puste`() = runTest {
        val result = stats.observeExerciseStats(requireNotNull(exerciseIds["squat"]), StatsRange.ALL).first()

        assertTrue(result.points.isEmpty())
        assertNull(result.records.heaviestSet)
        assertNull(result.rest.averageSeconds)
    }
}
