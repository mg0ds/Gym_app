package com.gymtracker.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.model.PlanExerciseDraft
import com.gymtracker.domain.model.RecurrenceDraft
import com.gymtracker.domain.model.ScheduleStatus
import com.gymtracker.domain.model.SessionStatus
import com.gymtracker.domain.repository.ScheduleSaveResult
import com.gymtracker.domain.schedule.ScheduleError
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.startOfDayEpochMs
import java.time.LocalDate
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
 * Kalendarz: rozwijanie reguły do wpisów, statusy, przenoszenie i dosypywanie
 * horyzontu. Pilnujemy tu trzech rzeczy:
 *
 *  * reguła zapisuje **przewidywalne** daty i rotuje dni planu (kryterium M3),
 *  * powtórne planowanie nie tworzy duplikatów (`UNIQUE(date, plan_day_id)`),
 *  * historia (odbyte, pominięte) przeżywa zmianę i usunięcie reguły.
 */
@RunWith(RobolectricTestRunner::class)
class CalendarRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var plans: PlanRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl
    private lateinit var calendar: CalendarRepositoryImpl

    private var exerciseId = 0L

    /** Reguły liczymy od tego dnia - dzięki temu daty w teście są konkretne. */
    private val today = LocalDate.of(2026, 10, 1)
    private var currentTime = 0L

    @Before
    fun setUp() = runTest {
        currentTime = today.startOfDayEpochMs() + MIDDAY_MS
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
        calendar = CalendarRepositoryImpl(
            database.calendarDao(),
            database.planDao(),
            database,
            timeProvider,
        )

        exerciseId = database.exerciseDao().insert(
            ExerciseEntity(
                externalId = "squat",
                name = "Barbell Back Squat",
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
    fun `reguła co 3 dni z rotacją zapisuje poprawne daty i dni planu`() = runTest {
        val planId = seedPlanA()

        val result = calendar.planRecurrence(
            RecurrenceDraft(planId = planId, startDate = today, endDate = today.plusDays(8), intervalDays = 3),
        )

        assertTrue(result is ScheduleSaveResult.Saved)
        assertEquals(3, (result as ScheduleSaveResult.Saved).count)

        val entries = calendar.observeRange(today, today.plusDays(8)).first()
        assertEquals(listOf(today, today.plusDays(3), today.plusDays(6)), entries.map { it.date })
        // Rotacja A -> B -> C, każdy wpis zna swój dzień planu i jego nazwę.
        assertEquals(listOf("Workout A", "Workout B", "Workout C"), entries.map { it.title })
        assertNotNull(entries.first().recurrenceId)
    }

    @Test
    fun `podgląd reguły pokazuje daty bez zapisu`() = runTest {
        val planId = seedPlanA()

        val preview = calendar.previewRecurrence(
            RecurrenceDraft(planId = planId, startDate = today, endDate = today.plusDays(5), intervalDays = 2),
        )

        assertTrue(preview.errors.isEmpty())
        assertEquals(listOf(today, today.plusDays(2), today.plusDays(4)), preview.workouts.map { it.date })
        assertEquals("nic nie zapisano", 0, database.calendarDao().countScheduled())
    }

    @Test
    fun `podgląd odrzuca plan bez dni`() = runTest {
        val emptyPlan = plans.createPlan(PlanDraft(name = "Pusty"))

        val noDays = calendar.previewRecurrence(
            RecurrenceDraft(planId = emptyPlan, startDate = today, intervalDays = 1),
        )
        assertEquals(listOf(ScheduleError.NO_PLAN_DAY), noDays.errors)
        assertTrue(noDays.workouts.isEmpty())
    }

    @Test
    fun `drugie planowanie tej samej reguły nie tworzy duplikatów`() = runTest {
        val planId = seedPlanA()
        val draft = RecurrenceDraft(planId = planId, startDate = today, endDate = today.plusDays(3), intervalDays = 3)

        val first = calendar.planRecurrence(draft) as ScheduleSaveResult.Saved
        calendar.planRecurrence(draft)

        assertEquals(2, first.count)
        assertEquals("jedna reguła, nie dwie", 1, calendar.observeRecurrencesOfPlan(planId).first().size)
        assertEquals("wpisy policzone raz", 2, database.calendarDao().countScheduled())
    }

    @Test
    fun `planowanie przez wybór dni pomija istniejące daty`() = runTest {
        val (planId, dayId) = seedPlanWithDay()

        val added = calendar.planDates(planId, dayId, listOf(today, today.plusDays(1), today))
        assertEquals(2, added)

        val again = calendar.planDates(planId, dayId, listOf(today))
        assertEquals(0, again)

        val entries = calendar.observeRange(today, today.plusDays(1)).first()
        assertEquals(2, entries.size)
        assertNull("wpis ręczny nie należy do reguły", entries.first().recurrenceId)
        assertEquals("Workout A", entries.first().title)
    }

    @Test
    fun `statusy wpisu - zrobiony i pominięty`() = runTest {
        val planId = seedPlanA()
        calendar.planRecurrence(
            RecurrenceDraft(planId = planId, startDate = today, endDate = today.plusDays(6), intervalDays = 3),
        )
        val entries = calendar.observeRange(today, today.plusDays(6)).first()

        calendar.setStatus(entries[0].id, ScheduleStatus.DONE)
        calendar.setStatus(entries[1].id, ScheduleStatus.SKIPPED)

        val updated = calendar.observeRange(today, today.plusDays(6)).first()
        assertEquals(
            listOf(ScheduleStatus.DONE, ScheduleStatus.SKIPPED, ScheduleStatus.PLANNED),
            updated.map { it.status },
        )
    }

    @Test
    fun `przeniesienie zmienia datę tego samego wpisu`() = runTest {
        val (planId, dayId) = seedPlanWithDay()
        calendar.planDates(planId, dayId, listOf(today))
        val entry = calendar.observeRange(today, today).first().single()

        calendar.move(entry.id, today.plusDays(4))

        assertTrue(calendar.observeRange(today, today).first().isEmpty())
        val moved = calendar.observeRange(today.plusDays(4), today.plusDays(4)).first().single()
        assertEquals(entry.id, moved.id)
        assertEquals(today.plusDays(4), moved.date)
    }

    @Test
    fun `usunięcie reguły zabiera plan a zostawia historię`() = runTest {
        val planId = seedPlanA()
        calendar.planRecurrence(
            RecurrenceDraft(planId = planId, startDate = today, endDate = today.plusDays(6), intervalDays = 3),
        )
        val entries = calendar.observeRange(today, today.plusDays(6)).first()
        val recurrenceId = requireNotNull(entries.first().recurrenceId)
        calendar.setStatus(entries[0].id, ScheduleStatus.DONE)
        calendar.setStatus(entries[1].id, ScheduleStatus.SKIPPED)

        calendar.deleteRecurrence(recurrenceId)

        val left = calendar.observeRange(today, today.plusDays(6)).first()
        assertEquals("zaplanowany wpis zniknął, historia została", 2, left.size)
        assertEquals(listOf(ScheduleStatus.DONE, ScheduleStatus.SKIPPED), left.map { it.status })
        assertNull("historia nie wisi już na regule", left.first().recurrenceId)
        assertNull(database.calendarDao().getRecurrence(recurrenceId))
    }

    @Test
    fun `zmiana reguły przelicza plan i zostawia historię`() = runTest {
        val planId = seedPlanA()
        calendar.planRecurrence(
            RecurrenceDraft(planId = planId, startDate = today, endDate = today.plusDays(9), intervalDays = 3),
        )
        val entries = calendar.observeRange(today, today.plusDays(9)).first()
        val recurrenceId = requireNotNull(entries.first().recurrenceId)
        calendar.setStatus(entries[0].id, ScheduleStatus.DONE)

        val result = calendar.replaceRecurrence(
            recurrenceId,
            RecurrenceDraft(planId = planId, startDate = today, endDate = today.plusDays(4), intervalDays = 4),
        )

        assertTrue(result is ScheduleSaveResult.Saved)
        val left = calendar.observeRange(today, today.plusDays(9)).first()
        assertTrue("odbyty trening został", left.any { it.status == ScheduleStatus.DONE })
        assertEquals(
            // 1.10 zostaje odbyte - nowa reguła nie nadpisuje historii,
            // więc dokłada wpis tylko na 5.10 (co 4 dni).
            "nowy plan: co 4 dni, bez nadpisywania historii",
            listOf(today.plusDays(4)),
            left.filter { it.status == ScheduleStatus.PLANNED }.map { it.date },
        )
    }

    @Test
    fun `dosypywanie horyzontu kontynuuje rotację`() = runTest {
        val planId = seedPlanA()
        val saved = calendar.planRecurrence(
            RecurrenceDraft(planId = planId, startDate = today, intervalDays = 3),
        ) as ScheduleSaveResult.Saved
        // 0..140 dni co 3 dni => 47 wpisów.
        assertEquals(47, saved.count)

        assertEquals("horyzont już wypełniony", 0, calendar.refreshHorizon())

        // Mijają 2 tygodnie - horyzont przesuwa się o 14 dni, dosypują się daty
        // 141, 144, 147, 150, 153 (maks. 3 dni odstępu od 1.10, nie od „dziś”).
        currentTime += 14 * 24 * 3600_000L
        val added = calendar.refreshHorizon()
        assertEquals(5, added)

        val fresh = calendar.observeRange(today.plusDays(139), today.plusDays(155)).first()
        assertEquals(
            listOf(
                today.plusDays(141),
                today.plusDays(144),
                today.plusDays(147),
                today.plusDays(150),
                today.plusDays(153),
            ),
            fresh.map { it.date },
        )
        // 47 wpisów: ostatni (46) to B, więc dalej C, A, B, C, A.
        assertEquals(
            listOf("Workout C", "Workout A", "Workout B", "Workout C", "Workout A"),
            fresh.map { it.title },
        )
    }

    @Test
    fun `reguła bez wpisów nie jest wskrzeszana`() = runTest {
        val planId = seedPlanA()
        calendar.planRecurrence(
            RecurrenceDraft(planId = planId, startDate = today, endDate = today.plusDays(6), intervalDays = 3),
        )
        calendar.observeRange(today, today.plusDays(6)).first().forEach { entry ->
            calendar.removeEntry(entry.id)
        }

        assertEquals(0, calendar.refreshHorizon())
        assertTrue(calendar.observeRange(today, today.plusDays(6)).first().isEmpty())
    }

    @Test
    fun `start z wpisu tworzy sesję i zakończenie odhacza wpis`() = runTest {
        val (planId, dayId) = seedPlanWithDay()
        calendar.planDates(planId, dayId, listOf(today))
        val entry = calendar.observeRange(today, today).first().single()

        val sessionId = requireNotNull(sessions.startFromSchedule(entry.id))
        val details = requireNotNull(sessions.observeSession(sessionId).first())
        assertEquals("Workout A", details.session.name)
        assertEquals(dayId, details.session.planDayId)
        assertTrue("ćwiczenia dnia trafiły do sesji", details.exercises.isNotEmpty())

        val linked = calendar.observeRange(today, today).first().single()
        assertEquals(sessionId, linked.sessionId)
        assertEquals(
            "rozpoczęty trening to jeszcze nie odbębniony dzień",
            ScheduleStatus.PLANNED,
            linked.status,
        )

        currentTime += 3600_000L
        sessions.finishSession(sessionId)

        val done = calendar.observeRange(today, today).first().single()
        assertEquals(ScheduleStatus.DONE, done.status)
        assertEquals(
            SessionStatus.FINISHED,
            requireNotNull(sessions.observeSession(sessionId).first()).session.status,
        )
    }

    @Test
    fun `przerwany trening nie odhacza wpisu`() = runTest {
        val (planId, dayId) = seedPlanWithDay()
        calendar.planDates(planId, dayId, listOf(today))
        val entry = calendar.observeRange(today, today).first().single()

        val sessionId = requireNotNull(sessions.startFromSchedule(entry.id))
        sessions.cancelSession(sessionId)

        assertEquals(ScheduleStatus.PLANNED, calendar.observeRange(today, today).first().single().status)
    }

    @Test
    fun `wpis bez dnia planu da się rozpocząć`() = runTest {
        val (planId, dayId) = seedPlanWithDay()
        calendar.planDates(planId, dayId, listOf(today))
        plans.deletePlan(planId)

        val entry = calendar.observeRange(today, today).first().single()
        assertNull("plan zniknął", entry.planDayId)

        val sessionId = requireNotNull(sessions.startFromSchedule(entry.id))
        val details = requireNotNull(sessions.observeSession(sessionId).first())
        assertEquals("Workout A", details.session.name)
        assertTrue("sesja bez planu nie ma ćwiczeń", details.exercises.isEmpty())
    }

    @Test
    fun `zakres dat filtruje wpisy`() = runTest {
        val (planId, dayId) = seedPlanWithDay()
        calendar.planDates(planId, dayId, listOf(today, today.plusDays(30)))

        assertEquals(1, calendar.observeRange(today, today.plusDays(10)).first().size)
        assertEquals(2, calendar.observeRange(today.minusDays(5), today.plusDays(40)).first().size)
        assertTrue(calendar.observeRange(today.plusDays(11), today.plusDays(29)).first().isEmpty())
    }

    @Test
    fun `usunięcie planu kasuje regułę a wpisy zostają bez wskazania`() = runTest {
        val (planId, dayId) = seedPlanWithDay()
        calendar.planRecurrence(
            RecurrenceDraft(
                planId = planId,
                planDayId = dayId,
                startDate = today,
                endDate = today.plusDays(3),
                intervalDays = 3,
            ),
        )
        assertEquals(1, calendar.observeRecurrencesOfPlan(planId).first().size)

        plans.deletePlan(planId)

        assertTrue(calendar.observeRecurrencesOfPlan(planId).first().isEmpty())
        val left = calendar.observeRange(today, today.plusDays(3)).first()
        assertEquals("wpisy zostają, ale tracą wskazanie", 2, left.size)
        assertTrue(left.all { it.planId == null && it.planDayId == null })
    }

    /** Plan z trzema dniami A/B/C - na nim testujemy rotację. */
    private suspend fun seedPlanA(): Long {
        val id = plans.createPlan(PlanDraft(name = "Plan ABC"))
        listOf("Workout A", "Workout B", "Workout C").forEach { name ->
            val dayId = plans.addDay(id, PlanDayDraft(name = name))
            plans.addExercise(dayId, PlanExerciseDraft(exerciseId = exerciseId, sets = 5, repsMin = 5, repsMax = 5))
        }
        return id
    }

    /** Plan z jednym dniem „Workout A”. */
    private suspend fun seedPlanWithDay(): Pair<Long, Long> {
        val id = plans.createPlan(PlanDraft(name = "Plan"))
        val dayId = plans.addDay(id, PlanDayDraft(name = "Workout A"))
        plans.addExercise(dayId, PlanExerciseDraft(exerciseId = exerciseId, sets = 5, repsMin = 5, repsMax = 5))
        return id to dayId
    }

    private companion object {
        const val MIDDAY_MS = 12 * 3600_000L
    }
}
