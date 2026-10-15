package com.gymtracker.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.domain.model.ExerciseDraft
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

    private var now = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = ExerciseRepositoryImpl(
            exerciseDao = database.exerciseDao(),
            timeProvider = TimeProvider { now },
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
    fun `usuniete cwiczenie znika z listy`() = runTest {
        val id = repository.addExercise(ExerciseDraft(name = "Pompki"))

        repository.deleteExercise(id)

        assertNull(repository.getExercise(id))
        assertEquals(emptyList<String>(), repository.observeExercises().first().map { it.name })
        assertEquals(0, repository.observeActiveCount().first())
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

        repository.deleteExercise(id)

        assertNull(repository.observeExercise(id).first())
    }
}
