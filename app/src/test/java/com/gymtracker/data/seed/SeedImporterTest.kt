package com.gymtracker.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
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
 * Import danych startowych. Sprawdzamy rzeczy, które łatwo zepsuć:
 *  * dane z JSON-a trafiają do bazy z normalizacją (link do filmu, mięśnie),
 *  * import jest idempotentny i **nie nadpisuje** zmian użytkownika,
 *  * prawdziwe pliki z APK (`assets/seed/...`) da się wczytać: 23 ćwiczenia
 *    i plan z trzema dniami,
 *  * plan wiąże się z katalogiem po slugu i nie wpada do niego ćwiczenie,
 *    którego w katalogu nie ma.
 */
@RunWith(RobolectricTestRunner::class)
class SeedImporterTest {

    private lateinit var database: AppDatabase

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
        val importer = importerWith(exercisesJson = FIXTURE_SEED)

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
        val importer = importerWith(exercisesJson = FIXTURE_SEED)

        importer.importExercisesIfNeeded()
        val secondRun = importer.importExercisesIfNeeded()

        assertNull("drugi import nie ma nic do zrobienia", secondRun)
        assertEquals(2, database.exerciseDao().count())
    }

    @Test
    fun `import nie nadpisuje zmian uzytkownika`() = runTest {
        val importer = importerWith(exercisesJson = FIXTURE_SEED)
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
        importerWith(exercisesJson = FIXTURE_SEED).importExercisesIfNeeded()

        val result = importerWith(exercisesJson = FIXTURE_SEED_NEW_VERSION).importExercisesIfNeeded()

        assertEquals(1, result?.imported)
        assertEquals(2, result?.skipped)
        assertEquals(3, database.exerciseDao().count())
    }

    @Test
    fun `import z danych w apce wczytuje wszystkie 23 cwiczenia`() = runTest {
        val importer = assetImporter()

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

    @Test
    fun `importuje plan z celem, alternatywa i komentarzem`() = runTest {
        val importer = importerWith(
            exercisesJson = FIXTURE_PROGRAM_EXERCISES,
            programs = mapOf("${SeedFiles.PROGRAMS_DIR}/test-plan.json" to FIXTURE_PROGRAM),
        )
        importer.importExercises()

        val result = importer.importPrograms()

        assertEquals(1, result.plansImported)
        assertEquals(0, result.plansSkipped)
        assertEquals(1, result.daysImported)
        assertEquals(4, result.positionsImported)
        assertEquals(1, result.alternativesImported)
        assertTrue(result.missingExercises.isEmpty())

        val plan = requireNotNull(database.planDao().getPlanByExternalId("test-plan"))
        assertEquals("Test Plan", plan.name)
        assertEquals("https://example.com/plan", plan.sourceUrl)
        assertEquals("Ktos", plan.author)

        val day = database.planDao().getDays(plan.id).single()
        assertEquals("Workout A", day.name)
        assertEquals(0, day.dayOfWeek)

        val exercises = database.planDao().getDayExercises(day.id)
        assertEquals(
            // Nazwa wiersza pochodzi z katalogu (plan trzyma tylko wskazanie).
            listOf("Barbell Back Squat", "Barbell Bench Press", "Military Press", "Plank"),
            exercises.map { row -> row.exerciseName },
        )

        // Komentarz z seeda („Ramped”) i cel 5×5.
        val squat = exercises.first()
        assertEquals(5, squat.entry.targetSets)
        assertEquals(5, squat.entry.targetRepsMin)
        assertEquals("reps", squat.entry.targetRepsUnit)
        assertEquals("Ramped", squat.entry.note)

        // „Bench Press albo Military Press”: wspólna grupa, jeden wybór domyślny.
        val bench = exercises[1].entry
        val military = exercises[2].entry
        assertNotNull(bench.alternativeGroup)
        assertEquals(bench.alternativeGroup, military.alternativeGroup)
        assertTrue(bench.isDefaultChoice)
        assertTrue(!military.isDefaultChoice)
        // Alternatywa dziedziczy cel ćwiczenia podstawowego.
        assertEquals(bench.targetSets, military.targetSets)

        // Deska to seria na czas: jednostka `seconds`, nie powtórzenia.
        val plank = exercises[3].entry
        assertEquals("seconds", plank.targetRepsUnit)
        assertEquals(60, plank.targetRepsMin)
    }

    @Test
    fun `ponowny import planow nie duplikuje`() = runTest {
        val importer = importerWith(
            exercisesJson = FIXTURE_PROGRAM_EXERCISES,
            programs = mapOf("${SeedFiles.PROGRAMS_DIR}/test-plan.json" to FIXTURE_PROGRAM),
        )
        importer.importExercises()

        importer.importProgramsIfNeeded()
        val secondRun = importer.importProgramsIfNeeded()

        assertNull("drugie wczytanie planów nie ma nic do zrobienia", secondRun)
        assertEquals(1, database.planDao().countPlans())
    }

    @Test
    fun `import planu pomija cwiczenia spoza katalogu`() = runTest {
        // Katalog bez „hover” - plan nie może dodać tego ćwiczenia.
        val importer = importerWith(
            exercisesJson = FIXTURE_PROGRAM_EXERCISES,
            programs = mapOf(
                "${SeedFiles.PROGRAMS_DIR}/test-plan.json" to
                    FIXTURE_PROGRAM.replace("\"exercise_id\": \"hover\"", "\"exercise_id\": \"nie-ma-takiego\""),
            ),
        )
        importer.importExercises()

        val result = importer.importPrograms()

        assertEquals(1, result.plansImported)
        assertEquals(3, result.positionsImported)
        assertEquals(listOf("nie-ma-takiego"), result.missingExercises)

        val plan = requireNotNull(database.planDao().getPlanByExternalId("test-plan"))
        val day = database.planDao().getDays(plan.id).single()
        // Pozycje są gęste (1..3) mimo brakującego ćwiczenia.
        assertEquals(
            listOf(1, 2, 3),
            database.planDao().getDayExercises(day.id).map { row -> row.entry.position },
        )
    }

    @Test
    fun `import planu z danych w apce daje trzy dni i wybor zamiast drugiego cwiczenia`() = runTest {
        val importer = assetImporter()
        importer.importExercises()

        val result = importer.importPrograms()

        assertEquals(1, result.plansImported)
        assertEquals(3, result.daysImported)
        assertTrue("wszystkie ćwiczenia planu są w katalogu", result.missingExercises.isEmpty())

        val plan = requireNotNull(
            database.planDao().getPlanByExternalId("muscle-strength-full-body-workout-routine"),
        )
        val days = database.planDao().getDays(plan.id)
        assertEquals(listOf("Workout A", "Workout B", "Workout C"), days.map { day -> day.name })
        assertEquals(listOf(0, 2, 4), days.map { day -> day.dayOfWeek })

        val dayB = days[1]
        val dayBExercises = database.planDao().getDayExercises(dayB.id)
        val pullUps = dayBExercises.first { row -> row.exerciseName == "Wide Grip Pull Up" }
        val invertedRows = dayBExercises.first { row -> row.exerciseName == "Feet Elevated Inverted Row" }
        assertEquals(pullUps.entry.alternativeGroup, invertedRows.entry.alternativeGroup)
        assertTrue(pullUps.entry.isDefaultChoice)
    }

    private fun assetImporter(): SeedImporter = SeedImporter(
        seedSource = AssetSeedSource(ApplicationProvider.getApplicationContext()),
        exerciseDao = database.exerciseDao(),
        planDao = database.planDao(),
        appMetaDao = database.appMetaDao(),
        timeProvider = TimeProvider { 1_000L },
    )

    private fun importerWith(
        exercisesJson: String,
        programs: Map<String, String> = emptyMap(),
    ): SeedImporter = SeedImporter(
        seedSource = FakeSeedSource(exercisesJson = exercisesJson, programs = programs),
        exerciseDao = database.exerciseDao(),
        planDao = database.planDao(),
        appMetaDao = database.appMetaDao(),
        timeProvider = TimeProvider { 1_000L },
    )

    /** Podstawia treść plików zamiast czytać je z APK. */
    private class FakeSeedSource(
        private val exercisesJson: String,
        private val programs: Map<String, String>,
    ) : SeedSource {

        override suspend fun readText(assetPath: String): String = when {
            assetPath == SeedFiles.EXERCISES -> exercisesJson
            else -> requireNotNull(programs[assetPath]) { "brak pliku $assetPath" }
        }

        override suspend fun list(assetDir: String): List<String> =
            if (assetDir == SeedFiles.PROGRAMS_DIR) {
                programs.keys
                    .filter { path -> path.startsWith("$assetDir/") }
                    .map { path -> path.substringAfterLast('/') }
                    .sorted()
            } else {
                emptyList()
            }
    }

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

        /** Katalog pod test planu. */
        const val FIXTURE_PROGRAM_EXERCISES = """
            {
              "schema_version": 1,
              "generated_at": "2026-01-02",
              "exercises": [
                { "id": "squat", "name": "Barbell Back Squat", "muscle_group": "Quads" },
                { "id": "barbell-bench-press", "name": "Barbell Bench Press", "muscle_group": "Chest" },
                { "id": "military-press", "name": "Military Press", "muscle_group": "Shoulders" },
                { "id": "hover", "name": "Plank", "muscle_group": "Abs" }
              ]
            }
        """

        /** Plan: ćwiczenie z komentarzem, wybór „albo” i seria na czas. */
        const val FIXTURE_PROGRAM = """
            {
              "schema_version": 1,
              "generated_at": "2026-01-02",
              "program": {
                "id": "test-plan",
                "name": "Test Plan",
                "source_url": "https://example.com/plan",
                "author": "Ktos",
                "schedule": [
                  { "day_label": "Monday", "day_of_week": 0, "workout_id": "test-plan-a" }
                ],
                "workouts": [
                  {
                    "id": "test-plan-a",
                    "name": "Workout A",
                    "order": 1,
                    "exercises": [
                      {
                        "order": 1, "exercise_id": "squat", "name": "Squats",
                        "sets": 5, "reps_min": 5, "reps_max": 5, "reps_raw": "5",
                        "modifier": "Ramped"
                      },
                      {
                        "order": 2, "exercise_id": "barbell-bench-press", "name": "Bench Press",
                        "sets": 3, "reps_min": 10, "reps_max": 10, "reps_raw": "10",
                        "alternatives": [
                          { "name": "Military Press", "exercise_id": "military-press" }
                        ],
                        "choice_note": "Bench Press or Military Press"
                      },
                      {
                        "order": 3, "exercise_id": "hover", "name": "Plank",
                        "sets": 3, "reps_min": 60, "reps_max": 60, "reps_raw": "60 seconds",
                        "tracking": { "reps": "time", "unit": "seconds" }
                      }
                    ]
                  }
                ]
              }
            }
        """
    }
}
