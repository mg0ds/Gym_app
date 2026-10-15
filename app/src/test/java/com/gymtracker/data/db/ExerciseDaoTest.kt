package com.gymtracker.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.daos.ExerciseDao
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.search.ExerciseSearchQuery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Testy katalogu ćwiczeń na JVM (Robolectric). Najważniejsze przypadki:
 * wyszukiwanie z polskimi znakami, filtr po partii, ulubione oraz to,
 * że indeks FTS sam się synchronizuje przy edycji i usunięciu.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = database.exerciseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `dodane cwiczenie jest widoczne na liscie`() = runTest {
        dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest"))

        val all = dao.observeExercises(ftsQuery = null, muscleGroup = null, favouritesOnly = false).first()

        assertEquals(1, all.size)
        assertEquals("Barbell Bench Press", all.single().name)
    }

    @Test
    fun `wyszukiwanie znajduje cwiczenie po nazwie`() = runTest {
        dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest"))
        dao.insert(exercise(name = "Barbell Back Squat", muscleGroup = "Quads"))

        val found = search("bench")

        assertEquals(listOf("Barbell Bench Press"), found.map { it.name })
    }

    @Test
    fun `wyszukiwanie ignoruje polskie znaki diakrytyczne`() = runTest {
        dao.insert(exercise(name = "Wyciskanie sztangą leżąc", muscleGroup = "Chest"))
        dao.insert(exercise(name = "Przysiad ze sztangą", muscleGroup = "Quads"))

        // Bez ogonków, po prefiksie i z ogonkami - zawsze ten sam wynik.
        assertEquals(
            listOf("Przysiad ze sztangą", "Wyciskanie sztangą leżąc"),
            search("sztanga").map { it.name },
        )
        assertEquals(
            listOf("Przysiad ze sztangą", "Wyciskanie sztangą leżąc"),
            search("sztangą").map { it.name },
        )
        assertEquals(
            listOf("Przysiad ze sztangą", "Wyciskanie sztangą leżąc"),
            search("sztan").map { it.name },
        )
    }

    @Test
    fun `wyszukiwanie laczy wyrazy spójnikiem AND`() = runTest {
        dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest"))
        dao.insert(exercise(name = "Barbell Back Squat", muscleGroup = "Quads"))

        assertEquals(listOf("Barbell Bench Press"), search("barbell bench").map { it.name })
        assertEquals(2, search("barbell").size)
    }

    @Test
    fun `wyszukiwanie znajduje po partii miesniowej i sprzecie`() = runTest {
        dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest", equipment = "Barbell"))
        dao.insert(exercise(name = "Cable Fly", muscleGroup = "Chest", equipment = "Cable"))

        assertEquals(listOf("Cable Fly"), search("cable").map { it.name })
        assertEquals(listOf("Barbell Bench Press", "Cable Fly"), search("chest").map { it.name })
    }

    @Test
    fun `puste szukanie zwraca cala liste`() = runTest {
        dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest"))
        dao.insert(exercise(name = "Cable Fly", muscleGroup = "Chest"))

        assertEquals(2, search("   ").size)
    }

    @Test
    fun `filtruje po partii miesniowej`() = runTest {
        dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest"))
        dao.insert(exercise(name = "Barbell Back Squat", muscleGroup = "Quads"))

        val chest = dao.observeExercises(ftsQuery = null, muscleGroup = "Chest", favouritesOnly = false).first()

        assertEquals(listOf("Barbell Bench Press"), chest.map { it.name })
        assertEquals(listOf("Chest", "Quads"), dao.observeMuscleGroups().first())
    }

    @Test
    fun `filtr ulubionych pokazuje tylko ulubione`() = runTest {
        val id = dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest"))
        dao.insert(exercise(name = "Barbell Back Squat", muscleGroup = "Quads"))

        dao.setFavourite(id = id, isFavourite = true, updatedAtEpochMs = 2_000L)

        val favourites = dao.observeExercises(ftsQuery = null, muscleGroup = null, favouritesOnly = true).first()

        assertEquals(listOf("Barbell Bench Press"), favourites.map { it.name })
    }

    @Test
    fun `indeks FTS aktualizuje sie po edycji i usunieciu cwiczenia`() = runTest {
        val id = dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest"))
        val saved = requireNotNull(dao.getById(id))

        dao.update(saved.copy(name = "Incline Bench Press"))

        assertEquals(emptyList<String>(), search("barbell").map { it.name })
        assertEquals(listOf("Incline Bench Press"), search("incline").map { it.name })

        dao.deleteById(id)

        assertEquals(emptyList<String>(), search("incline").map { it.name })
        assertEquals(0, dao.count())
    }

    @Test
    fun `import pomija cwiczenia o istniejacym external_id`() = runTest {
        dao.insertIgnoringDuplicates(
            listOf(exercise(name = "Barbell Bench Press", externalId = "barbell-bench-press")),
        )

        val results = dao.insertIgnoringDuplicates(
            listOf(
                exercise(name = "Bench Press (zmienione przez uzytkownika)", externalId = "barbell-bench-press"),
                exercise(name = "Barbell Back Squat", externalId = "squat"),
            ),
        )

        // Pominięty wiersz zwraca -1, nowy dostaje swoje id; istniejący wpis
        // zostaje nietknięty (import nigdy nie nadpisuje zmian użytkownika).
        assertEquals(-1L, results.first())
        assertEquals(true, results[1] > 0)
        assertEquals(2, dao.count())
        assertEquals("Barbell Bench Press", dao.getByExternalId("barbell-bench-press")?.name)
        assertEquals("Barbell Back Squat", dao.getByExternalId("squat")?.name)
    }

    @Test
    fun `archiwalne cwiczenia nie trafiaja na liste`() = runTest {
        dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest"))
        dao.insert(exercise(name = "Cable Fly", muscleGroup = "Chest", isArchived = true))

        assertEquals(listOf("Barbell Bench Press"), search(null).map { it.name })
        assertEquals(1, dao.observeActiveCount().first())
    }

    @Test
    fun `observeById zwraca cwiczenie i null po usunieciu`() = runTest {
        val id = dao.insert(exercise(name = "Barbell Bench Press", muscleGroup = "Chest"))

        assertNotNull(dao.observeById(id).first())

        dao.deleteById(id)

        assertNull(dao.observeById(id).first())
    }

    private suspend fun search(rawQuery: String?): List<ExerciseEntity> =
        dao.observeExercises(
            ftsQuery = ExerciseSearchQuery.build(rawQuery),
            muscleGroup = null,
            favouritesOnly = false,
        ).first()

    private fun exercise(
        name: String,
        externalId: String? = null,
        muscleGroup: String? = null,
        equipment: String? = null,
        isArchived: Boolean = false,
    ) = ExerciseEntity(
        externalId = externalId,
        name = name,
        muscleGroup = muscleGroup,
        equipment = equipment,
        isArchived = isArchived,
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
    )
}
