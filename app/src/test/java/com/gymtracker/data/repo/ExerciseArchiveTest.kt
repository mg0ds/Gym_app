package com.gymtracker.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseMediaEntity
import com.gymtracker.domain.model.ExerciseDraft
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.model.PlanExerciseDraft
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
 * Archiwum ćwiczeń (M9).
 *
 * Archiwizacja to nie kosz na pliki, a odroczenie decyzji: ćwiczenie znika
 * z katalogu, ale dalej trzyma plany i historię. Ten test pilnuje obu stron tej
 * umowy - że przywrócenie oddaje ćwiczenie dokładnie takie, jakie było, i że
 * skasowanie na stałe naprawdę zabiera to, o czym uprzedza ekran (pozycje
 * w planach, wpisy w historii, załączniki).
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseArchiveTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ExerciseRepositoryImpl
    private lateinit var planRepository: PlanRepositoryImpl
    private lateinit var sessionRepository: SessionRepositoryImpl

    private var now = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        val timeProvider = TimeProvider { now }
        repository = ExerciseRepositoryImpl(
            exerciseDao = database.exerciseDao(),
            planDao = database.planDao(),
            sessionDao = database.sessionDao(),
            timeProvider = timeProvider,
        )
        planRepository = PlanRepositoryImpl(
            planDao = database.planDao(),
            database = database,
            timeProvider = timeProvider,
        )
        sessionRepository = SessionRepositoryImpl(
            sessionDao = database.sessionDao(),
            setLogDao = database.setLogDao(),
            planDao = database.planDao(),
            calendarDao = database.calendarDao(),
            exerciseDao = database.exerciseDao(),
            database = database,
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `przywrocenie oddaje cwiczenie do katalogu`() = runTest {
        val id = repository.addExercise(ExerciseDraft(name = "Pompki", muscleGroup = "Chest"))
        repository.archiveExercise(id)

        assertEquals(listOf("Pompki"), repository.observeArchivedExercises().first().map { it.name })
        assertEquals(1, repository.observeArchivedCount().first())

        now = 5_000L
        repository.restoreExercise(id)

        assertEquals(emptyList<String>(), repository.observeArchivedExercises().first().map { it.name })
        assertEquals(0, repository.observeArchivedCount().first())
        val exercise = requireNotNull(repository.getExercise(id))
        assertEquals("Pompki", exercise.name)
        assertFalse(exercise.isArchived)
        // Wiersz wrócił w to samo miejsce: jest jednym z ćwiczeń katalogu, nie nowym.
        assertEquals(listOf("Pompki"), repository.observeExercises().first().map { it.name })
        assertEquals(1, repository.observeActiveCount().first())
    }

    @Test
    fun `archiwum idzie po nazwie niezaleznie od wielkosci liter`() = runTest {
        val pompki = repository.addExercise(ExerciseDraft(name = "Pompki"))
        val przysiad = repository.addExercise(ExerciseDraft(name = "przysiad"))
        repository.archiveExercise(pompki)
        repository.archiveExercise(przysiad)

        assertEquals(listOf("Pompki", "przysiad"), repository.observeArchivedExercises().first().map { it.name })
    }

    @Test
    fun `cena oproznienia liczy plany, historie i zalaczniki`() = runTest {
        val exerciseId = repository.addExercise(ExerciseDraft(name = "Pompki"))
        val otherId = repository.addExercise(ExerciseDraft(name = "Plank"))

        val planId = planRepository.createPlan(PlanDraft(name = "Plan"))
        val dayId = planRepository.addDay(planId, PlanDayDraft(name = "Dzień A"))
        planRepository.addExercise(dayId, PlanExerciseDraft(exerciseId = exerciseId))
        // Drugie ćwiczenie w tym samym dniu i tak nie wchodzi do ceny archiwum,
        // bo nie jest zarchiwizowane.
        planRepository.addExercise(dayId, PlanExerciseDraft(exerciseId = otherId))
        val sessionId = requireNotNull(sessionRepository.startFromPlanDay(dayId))
        sessionRepository.finishSession(sessionId)
        database.mediaDao().insert(
            ExerciseMediaEntity(
                exerciseId = exerciseId,
                kind = "image",
                remoteUri = "content://media/external/images/media/42",
                position = 0,
                createdAtEpochMs = 1_000L,
            ),
        )

        repository.archiveExercise(exerciseId)

        val impact = repository.observeArchiveImpact().first()

        assertEquals(1, impact.exercises)
        assertEquals(1, impact.planPositions)
        assertEquals(1, impact.sessionPositions)
        assertEquals(1, impact.media)
        assertTrue(impact.touchesDataElsewhere)
    }

    @Test
    fun `puste archiwum nie ma czego zabrac`() = runTest {
        val id = repository.addExercise(ExerciseDraft(name = "Pompki"))

        // Ćwiczenie w katalogu, nie w archiwum: cena opróżnienia jest zerowa,
        // więc ekran nie straszy użytkownika utratą danych, których nie ma.
        val impact = repository.observeArchiveImpact().first()

        assertTrue(impact.isEmpty)
        assertFalse(impact.touchesDataElsewhere)
        assertEquals(0, repository.emptyArchive())
        assertEquals(listOf(id), repository.observeExercises().first().map { it.id })
    }

    @Test
    fun `skasowanie na stale zabiera pozycje w planach i wpis w historii`() = runTest {
        val exerciseId = repository.addExercise(ExerciseDraft(name = "Pompki"))

        val planId = planRepository.createPlan(PlanDraft(name = "Plan"))
        val dayId = planRepository.addDay(planId, PlanDayDraft(name = "Dzień A"))
        planRepository.addExercise(dayId, PlanExerciseDraft(exerciseId = exerciseId))
        val sessionId = requireNotNull(sessionRepository.startFromPlanDay(dayId))
        sessionRepository.finishSession(sessionId)

        repository.archiveExercise(exerciseId)
        repository.deleteExercisePermanently(exerciseId)

        // Wiersz zniknął z bazy na dobre - nawet dla historii, która szuka po
        // `getAnyById` (archiwizacja zostawiała go czytelnym, kasowanie nie).
        assertNull(database.exerciseDao().getAnyById(exerciseId))
        // Kaskada sprzątnęła wszystko: cenę opróżnienia liczy to samo zapytanie,
        // które widzi ekran, więc zero jest tu mocniejszą asercją niż brak wiersza.
        val impact = repository.observeArchiveImpact().first()
        assertEquals(0, impact.exercises)
        assertEquals(0, impact.planPositions)
        assertEquals(0, impact.sessionPositions)
        assertEquals(0, impact.media)
        // Dzień planu i trening zostają - bez tego ćwiczenia, ale czytelne.
        val day = requireNotNull(planRepository.observeDay(dayId).first())
        assertEquals(emptyList<String>(), day.exercises.map { it.exerciseName })
        val session = requireNotNull(sessionRepository.observeSession(sessionId).first())
        assertEquals(emptyList<String>(), session.exercises.map { it.exerciseName })
    }

    @Test
    fun `oproznienie archiwum nie rusza katalogu`() = runTest {
        val archivedId = repository.addExercise(ExerciseDraft(name = "Pompki"))
        val keptId = repository.addExercise(ExerciseDraft(name = "Plank"))
        repository.archiveExercise(archivedId)

        val deleted = repository.emptyArchive()

        assertEquals(1, deleted)
        assertNull(database.exerciseDao().getAnyById(archivedId))
        assertEquals(listOf(keptId), repository.observeExercises().first().map { it.id })
        assertEquals(0, repository.observeArchivedCount().first())
        assertEquals(1, repository.observeActiveCount().first())
    }
}
