package com.gymtracker.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Testy repozytorium katalogu - sprawdzają kontrakt, na którym opiera się UI:
 * dodawanie własnych ćwiczeń, edycja bez gubienia danych z importu,
 * ulubione, usuwanie i wyszukiwanie.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseRepositoryTest {

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
            database = database,
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `dodaje wlasne cwiczenie i widzi je na liscie`() = runTest {
        val id = repository.addExercise(
            ExerciseDraft(name = "  Wyciskanie hantli  ", muscleGroup = "Chest"),
        )

        val exercise = requireNotNull(repository.getExercise(id))

        assertEquals("Wyciskanie hantli", exercise.name)
        assertEquals("Chest", exercise.muscleGroup)
        assertNull(exercise.externalId)
        assertTrue(exercise.isUserCreated)
        assertEquals(listOf("Wyciskanie hantli"), repository.observeExercises().first().map { it.name })
    }

    @Test
    fun `wlasne cwiczenie jest znajdowane po polsku bez ogonkow`() = runTest {
        repository.addExercise(ExerciseDraft(name = "Wyciskanie sztangą stojąc"))

        val found = repository.observeExercises(query = "sztanga").first()

        assertEquals(listOf("Wyciskanie sztangą stojąc"), found.map { it.name })
    }

    @Test
    fun `filtruje po partii i po ulubionych`() = runTest {
        val chestId = repository.addExercise(ExerciseDraft(name = "Pompki", muscleGroup = "Chest"))
        repository.addExercise(ExerciseDraft(name = "Przysiad", muscleGroup = "Quads"))
        repository.setFavourite(chestId, isFavourite = true)

        val chest = repository.observeExercises(muscleGroup = "Chest").first()
        val favourites = repository.observeExercises(favouritesOnly = true).first()

        assertEquals(listOf("Pompki"), chest.map { it.name })
        assertEquals(listOf("Pompki"), favourites.map { it.name })
        assertTrue(chest.single().isFavourite)
    }

    @Test
    fun `edycja zmienia pola ale nie gubi danych z importu`() = runTest {
        val importedId = database.exerciseDao().insert(
            ExerciseEntity(
                externalId = "barbell-bench-press",
                name = "Barbell Bench Press",
                nameInProgram = "Bench Press",
                sourceUrl = "https://example.com/bench",
                muscleGroup = "Chest",
                muscleGroupSlug = "chest",
                isFavourite = true,
                createdAtEpochMs = 1_000L,
                updatedAtEpochMs = 1_000L,
            ),
        )

        now = 5_000L
        repository.updateExercise(
            importedId,
            ExerciseDraft(name = "Wyciskanie leżąc", muscleGroup = "Chest", defaultSets = 5),
        )

        val updated = requireNotNull(repository.getExercise(importedId))
        val raw = requireNotNull(database.exerciseDao().getById(importedId))

        assertEquals("Wyciskanie leżąc", updated.name)
        assertEquals(5, updated.defaultSets)
        // Pola z danych startowych zostają - import nadal rozpozna ten wiersz.
        assertEquals("barbell-bench-press", updated.externalId)
        assertEquals("Bench Press", updated.nameInProgram)
        assertEquals("https://example.com/bench", updated.sourceUrl)
        assertEquals("chest", updated.muscleGroupSlug)
        assertTrue(updated.isFavourite)
        assertFalse(updated.isUserCreated)
        // Znaczniki czasu: data utworzenia bez zmian, data edycji podbita.
        assertEquals(1_000L, raw.createdAtEpochMs)
        assertEquals(5_000L, raw.updatedAtEpochMs)
    }

    @Test
    fun `link do filmu normalizuje sie przy zapisie`() = runTest {
        val id = repository.addExercise(
            ExerciseDraft(name = "Martwy ciąg", youtubeUrl = "youtu.be/R2dMsNhN3DE"),
        )

        assertEquals(
            "https://www.youtube.com/watch?v=R2dMsNhN3DE",
            repository.getExercise(id)?.youtubeUrl,
        )
    }

    @Test
    fun `puste komentarze i miesnie dodatkowe nie zapisuja smieci`() = runTest {
        val id = repository.addExercise(
            ExerciseDraft(
                name = "Plank",
                notes = "   ",
                secondaryMuscles = listOf("Abs", "  ", "Abs"),
            ),
        )

        val exercise = requireNotNull(repository.getExercise(id))

        assertNull(exercise.notes)
        assertEquals(listOf("Abs"), exercise.secondaryMuscles)
    }

    @Test
    fun `zarchiwizowane cwiczenie znika z katalogu, ale zostaje w bazie`() = runTest {
        val id = repository.addExercise(ExerciseDraft(name = "Pompki", muscleGroup = "Chest"))

        repository.archiveExercise(id)

        // Zniknęło z katalogu, wyszukiwania i filtrów...
        assertNull(repository.getExercise(id))
        assertNull(repository.observeExercise(id).first())
        assertEquals(emptyList<String>(), repository.observeExercises().first().map { it.name })
        assertEquals(emptyList<String>(), repository.observeMuscleGroups().first())
        assertEquals(0, repository.observeActiveCount().first())
        // ...ale wiersz istnieje dalej: to on trzyma plany i historię treningów.
        // `count` liczy wszystkie wiersze, `observeActiveCount` tylko aktywne.
        assertEquals(1, database.exerciseDao().count())
    }

    @Test
    fun `zarchiwizowane cwiczenie zostaje w planie i w historii treningu`() = runTest {
        val exerciseId = repository.addExercise(ExerciseDraft(name = "Pompki"))
        val planId = planRepository.createPlan(PlanDraft(name = "Plan"))
        val dayId = planRepository.addDay(planId, PlanDayDraft(name = "Dzień A"))
        planRepository.addExercise(dayId, PlanExerciseDraft(exerciseId = exerciseId))
        val sessionId = requireNotNull(sessionRepository.startFromPlanDay(dayId))
        sessionRepository.finishSession(sessionId)

        repository.archiveExercise(exerciseId)

        // Plan i trening nadal mają tę pozycję - z nazwą czytelną dla człowieka.
        val day = requireNotNull(planRepository.observeDay(dayId).first())
        assertEquals(listOf("Pompki"), day.exercises.map { it.exerciseName })
        val session = requireNotNull(sessionRepository.observeSession(sessionId).first())
        assertEquals(listOf("Pompki"), session.exercises.map { it.exerciseName })
    }

    @Test
    fun `edycja nieistniejacego cwiczenia nic nie psuje`() = runTest {
        repository.updateExercise(999L, ExerciseDraft(name = "Duch"))

        assertNull(repository.getExercise(999L))
        assertEquals(0, repository.observeActiveCount().first())
    }

    @Test
    fun `partie miesniowe do filtrow pochodza z katalogu`() = runTest {
        repository.addExercise(ExerciseDraft(name = "Pompki", muscleGroup = "Chest"))
        repository.addExercise(ExerciseDraft(name = "Przysiad", muscleGroup = "Quads"))
        repository.addExercise(ExerciseDraft(name = "Plank", muscleGroup = "Abs"))

        assertEquals(listOf("Abs", "Chest", "Quads"), repository.observeMuscleGroups().first())
    }

    @Test
    fun `obserwacja pojedynczego cwiczenia reaguje na zmiany`() = runTest {
        val id = repository.addExercise(ExerciseDraft(name = "Pompki"))

        assertNotNull(repository.observeExercise(id).first())

        repository.archiveExercise(id)

        assertNull(repository.observeExercise(id).first())
    }

    @Test
    fun `nieuzywane cwiczenie nie ma uzyc`() = runTest {
        val id = repository.addExercise(ExerciseDraft(name = "Pompki"))

        val usage = repository.observeUsage(id).first()

        assertEquals(0, usage.planPositions)
        assertEquals(0, usage.sessionPositions)
    }

    @Test
    fun `uzycie liczy pozycje w planach i w treningach`() = runTest {
        val exerciseId = repository.addExercise(ExerciseDraft(name = "Pompki"))
        val otherId = repository.addExercise(ExerciseDraft(name = "Plank"))

        // Ćwiczenie w planie: dwa dni po jednej pozycji.
        val planId = planRepository.createPlan(PlanDraft(name = "Plan"))
        val dayA = planRepository.addDay(planId, PlanDayDraft(name = "Dzień A"))
        val dayB = planRepository.addDay(planId, PlanDayDraft(name = "Dzień B"))
        planRepository.addExercise(dayA, PlanExerciseDraft(exerciseId = exerciseId))
        planRepository.addExercise(dayB, PlanExerciseDraft(exerciseId = exerciseId))
        // Inne ćwiczenie w tym samym dniu nie może się wliczać.
        planRepository.addExercise(dayA, PlanExerciseDraft(exerciseId = otherId))

        // Historia: zakończony trening z dnia A.
        val sessionId = requireNotNull(sessionRepository.startFromPlanDay(dayA))
        sessionRepository.finishSession(sessionId)

        val usage = repository.observeUsage(exerciseId).first()

        assertEquals(2, usage.planPositions)
        assertEquals(1, usage.sessionPositions)
        // Ćwiczenie z jednym dniem ma dokładnie jedną pozycję w planie.
        assertEquals(1, repository.observeUsage(otherId).first().planPositions)
    }
}
