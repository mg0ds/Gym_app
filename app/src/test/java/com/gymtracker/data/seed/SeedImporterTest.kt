package com.gymtracker.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.domain.time.TimeProvider
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
 * Import danych startowych. Sprawdzamy trzy rzeczy, które łatwo zepsuć:
 *  * dane z JSON-a trafiają do bazy z normalizacją (link do filmu, mięśnie),
 *  * import jest idempotentny i **nie nadpisuje** zmian użytkownika,
 *  * prawdziwy plik z APK (`assets/seed/exercises.json`) da się wczytać i ma
 *    wszystkie 23 ćwiczenia.
 */
@RunWith(RobolectricTestRunner::class)
class SeedImporterTest {

    private lateinit var database: AppDatabase
    private lateinit var importer: SeedImporter

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `importuje cwiczenia z pliku i normalizuje dane`() = runTest {
        importer = importerWith(FIXTURE_SEED)

        val result = importer.importExercises()

        assertEquals(2, result.imported)
        assertEquals(0, result.skipped)
        assertEquals("2026-01-02", result.seedVersion)

        val bench = requireNotNull(database.exerciseDao().getByExternalId("barbell-bench-press"))
        assertEquals("Barbell Bench Press", bench.name)
        assertEquals("Bench Press", bench.nameInProgram)
        assertEquals("Chest", bench.muscleGroup)
        assertEquals("https://www.youtube.com/watch?v=tuwHzzPdaGc", bench.youtubeUrl)
        assertEquals("""["Shoulders","Triceps"]""", bench.secondaryMusclesJson)
        assertEquals("https://example.com/bench", bench.sourceUrl)

        val squat = requireNotNull(database.exerciseDao().getByExternalId("squat"))
        assertNull(squat.youtubeUrl)
    }

    @Test
    fun `ponowny import nie duplikuje danych`() = runTest {
        importer = importerWith(FIXTURE_SEED)

        importer.importExercisesIfNeeded()
        val secondRun = importer.importExercisesIfNeeded()

        assertNull("drugi import nie ma nic do zrobienia", secondRun)
        assertEquals(2, database.exerciseDao().count())
    }

    @Test
    fun `import nie nadpisuje zmian uzytkownika`() = runTest {
        importer = importerWith(FIXTURE_SEED)
        importer.importExercises()

        val bench = requireNotNull(database.exerciseDao().getByExternalId("barbell-bench-press"))
        database.exerciseDao().update(bench.copy(name = "Moja nazwa", isFavourite = true))

        // Ten sam plik, ale import wymuszony (np. przycisk w ustawieniach).
        val result = importer.importExercises()

        assertEquals(0, result.imported)
        assertEquals(2, result.skipped)
        assertEquals(2, database.exerciseDao().count())
        assertEquals(
            "Moja nazwa",
            database.exerciseDao().getByExternalId("barbell-bench-press")?.name,
        )
    }

    @Test
    fun `nowsza wersja danych dopisuje brakujace cwiczenia`() = runTest {
        importer = importerWith(FIXTURE_SEED)
        importer.importExercisesIfNeeded()

        importer = importerWith(FIXTURE_SEED_NEW_VERSION)
        val result = importer.importExercisesIfNeeded()

        assertEquals(1, result?.imported)
        assertEquals(2, result?.skipped)
        assertEquals(3, database.exerciseDao().count())
    }

    @Test
    fun `import z danych w apce wczytuje wszystkie 23 cwiczenia`() = runTest {
        importer = SeedImporter(
            seedSource = AssetSeedSource(ApplicationProvider.getApplicationContext()),
            exerciseDao = database.exerciseDao(),
            appMetaDao = database.appMetaDao(),
            timeProvider = TimeProvider { 1_000L },
        )

        val result = importer.importExercises()

        assertEquals(23, result.imported)
        assertEquals(23, database.exerciseDao().count())

        // Sprawdzamy, że indeks wyszukiwania dostał dane z importu.
        val found = database.exerciseDao()
            .observeExercises(ftsQuery = "bench*", muscleGroup = null, favouritesOnly = false)
            .first()
        assertTrue("szukanie po imporcie działa", found.any { it.externalId == "barbell-bench-press" })

        val version = database.appMetaDao().get(SeedFiles.META_EXERCISES_VERSION)
        assertTrue("wersja seeda zapisana w app_meta", !version.isNullOrBlank())
    }

    private fun importerWith(seedJson: String): SeedImporter = SeedImporter(
        seedSource = object : SeedSource {
            override suspend fun readText(assetPath: String): String = seedJson
        },
        exerciseDao = database.exerciseDao(),
        appMetaDao = database.appMetaDao(),
        timeProvider = TimeProvider { 1_000L },
    )

    private companion object {
        const val FIXTURE_SEED = """
            {
              "schema_version": 1,
              "generated_at": "2026-01-02",
              "exercises": [
                {
                  "id": "barbell-bench-press",
                  "name": "Barbell Bench Press",
                  "source_url": "https://example.com/bench",
                  "muscle_group": "Chest",
                  "muscle_group_slug": "chest",
                  "secondary_muscles": ["Shoulders", "Triceps"],
                  "equipment": "Barbell",
                  "mechanics": "Compound",
                  "level": "Intermediate",
                  "youtube_id": "tuwHzzPdaGc",
                  "name_in_program": "Bench Press"
                },
                {
                  "id": "squat",
                  "name": "Barbell Back Squat",
                  "muscle_group": "Quads",
                  "muscle_group_slug": "quads",
                  "equipment": "Barbell",
                  "mechanics": "Compound",
                  "level": "Intermediate",
                  "youtube_id": null,
                  "name_in_program": "Squats"
                }
              ]
            }
        """

        /** Ten sam plik + jedno nowe ćwiczenie (symulacja aktualizacji danych). */
        const val FIXTURE_SEED_NEW_VERSION = """
            {
              "schema_version": 1,
              "generated_at": "2026-02-01",
              "exercises": [
                {
                  "id": "barbell-bench-press",
                  "name": "Barbell Bench Press",
                  "muscle_group": "Chest",
                  "equipment": "Barbell"
                },
                {
                  "id": "squat",
                  "name": "Barbell Back Squat",
                  "muscle_group": "Quads",
                  "equipment": "Barbell"
                },
                {
                  "id": "military-press",
                  "name": "Military Press",
                  "muscle_group": "Shoulders",
                  "equipment": "Barbell"
                }
              ]
            }
        """
    }
}
