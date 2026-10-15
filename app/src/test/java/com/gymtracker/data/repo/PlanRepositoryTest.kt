package com.gymtracker.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.domain.model.PlanDayDraft
import com.gymtracker.domain.model.PlanDraft
import com.gymtracker.domain.model.PlanExerciseDraft
import com.gymtracker.domain.model.PlanExerciseTargets
import com.gymtracker.domain.model.RepsUnit
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
 * Logika planów: dni, kolejność (przeciąganie), superserie i alternatywy.
 * Sprawdzamy to na prawdziwej bazie w pamięci, bo właśnie na styku z SQL-em
 * (UNIQUE na pozycji dnia, kaskady) najłatwiej coś zepsuć.
 */
@RunWith(RobolectricTestRunner::class)
class PlanRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PlanRepositoryImpl
    private val exerciseIds = mutableMapOf<String, Long>()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = PlanRepositoryImpl(
            planDao = database.planDao(),
            database = database,
            timeProvider = TimeProvider { 1_000L },
        )
        listOf("squat" to "Barbell Back Squat", "bench" to "Barbell Bench Press", "row" to "Barbell Row")
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
    fun `dodaje plan z dniem i cwiczeniem razem z celem`() = runTest {
        val planId = repository.createPlan(PlanDraft(name = "  Mój plan  ", description = ""))
        val dayId = repository.addDay(planId, PlanDayDraft(name = "Dzień A", dayOfWeek = 0))
        repository.addExercise(
            dayId,
            PlanExerciseDraft(
                exerciseId = requireNotNull(exerciseIds["squat"]),
                sets = 5,
                repsMin = 5,
                repsMax = 5,
                restSeconds = 120,
            ),
        )

        val details = requireNotNull(repository.observePlanDetails(planId).first())
        assertEquals("Mój plan", details.plan.name)
        assertNull("pusty opis zostaje pusty", details.plan.description)

        val day = details.days.single()
        assertEquals("Dzień A", day.name)
        assertEquals(0, day.dayOfWeek)

        val exercise = day.exercises.single()
        assertEquals("Barbell Back Squat", exercise.exerciseName)
        assertEquals(5, exercise.sets)
        assertEquals(5, exercise.repsMin)
        assertEquals(120, exercise.restSeconds)
        assertEquals(RepsUnit.REPS, exercise.repsUnit)
        assertEquals(1, details.exerciseCount)
    }

    @Test
    fun `przestawianie dni i cwiczen zmienia kolejnosc`() = runTest {
        val planId = repository.createPlan(PlanDraft(name = "Plan"))
        val a = repository.addDay(planId, PlanDayDraft(name = "A"))
        val b = repository.addDay(planId, PlanDayDraft(name = "B"))
        val c = repository.addDay(planId, PlanDayDraft(name = "C"))

        repository.reorderDays(planId, listOf(c, a, b))

        assertEquals(
            listOf("C", "A", "B"),
            requireNotNull(repository.observePlanDetails(planId).first()).days.map { it.name },
        )

        val squat = repository.addExercise(a, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["squat"])))
        val bench = repository.addExercise(a, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["bench"])))
        val row = repository.addExercise(a, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["row"])))

        repository.reorderExercises(a, listOf(row, squat, bench))

        val dayA = requireNotNull(repository.observeDay(a).first())
        assertEquals(listOf("Barbell Row", "Barbell Back Squat", "Barbell Bench Press"), dayA.exercises.map { it.exerciseName })
        // Pozycje numerują się gęsto od 1.
        assertEquals(listOf(1, 2, 3), dayA.exercises.map { it.position })
    }

    @Test
    fun `rozjechana lista przy przestawianiu niczego nie psuje`() = runTest {
        val planId = repository.createPlan(PlanDraft(name = "Plan"))
        val dayId = repository.addDay(planId, PlanDayDraft(name = "A"))
        val squat = repository.addExercise(dayId, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["squat"])))
        repository.addExercise(dayId, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["bench"])))

        // Lista nie zawiera wszystkich pozycji dnia - nie ruszamy kolejności.
        repository.reorderExercises(dayId, listOf(squat))

        val day = requireNotNull(repository.observeDay(dayId).first())
        assertEquals(listOf("Barbell Back Squat", "Barbell Bench Press"), day.exercises.map { it.exerciseName })
    }

    @Test
    fun `superseria laczy dwie pozycje i rozlacza je`() = runTest {
        val planId = repository.createPlan(PlanDraft(name = "Plan"))
        val dayId = repository.addDay(planId, PlanDayDraft(name = "A"))
        val first = repository.addExercise(dayId, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["squat"])))
        val second = repository.addExercise(dayId, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["bench"])))

        repository.linkSuperset(first, second)

        var day = requireNotNull(repository.observeDay(dayId).first())
        val group = day.exercises.map { it.supersetGroup }.distinct().single()
        assertNotNull("obie pozycje w jednej superserii", group)

        repository.unlinkSuperset(first)

        day = requireNotNull(repository.observeDay(dayId).first())
        assertTrue("po rozłączeniu nie zostaje samotny członek", day.exercises.all { it.supersetGroup == null })
    }

    @Test
    fun `alternatywa dolacza do grupy i mozna zmienic wybor domyslny`() = runTest {
        val planId = repository.createPlan(PlanDraft(name = "Plan"))
        val dayId = repository.addDay(planId, PlanDayDraft(name = "A"))
        val primary = repository.addExercise(
            dayId,
            PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["squat"]), sets = 5, repsMin = 5, repsMax = 5),
        )

        val alternative = repository.addAlternative(
            primary,
            PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["bench"])),
        )

        var day = requireNotNull(repository.observeDay(dayId).first())
        val group = day.exercises.map { it.alternativeGroup }.distinct().single()
        assertNotNull(group)
        assertEquals(5, day.exercises.first { it.id == alternative }.sets)

        repository.makeDefaultChoice(alternative)

        day = requireNotNull(repository.observeDay(dayId).first())
        assertTrue("teraz domyślna jest alternatywa", day.exercises.first { it.id == alternative }.isDefaultChoice)
        assertTrue("stara domyślna straciła pierwszeństwo", !day.exercises.first { it.id == primary }.isDefaultChoice)
        assertTrue(day.exercises.first { it.id == primary }.isAlternative)
    }

    @Test
    fun `usuniecie podstawowego cwiczenia konczy grupe albo`() = runTest {
        val planId = repository.createPlan(PlanDraft(name = "Plan"))
        val dayId = repository.addDay(planId, PlanDayDraft(name = "A"))
        val primary = repository.addExercise(dayId, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["squat"])))
        val alternative = repository.addAlternative(primary, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["bench"])))

        repository.removeExercise(primary)

        val day = requireNotNull(repository.observeDay(dayId).first())
        val left = day.exercises.single()
        assertEquals(alternative, left.id)
        assertNull("samotne ćwiczenie nie jest już wyborem", left.alternativeGroup)
        assertTrue(left.isDefaultChoice)
    }

    @Test
    fun `zmiana celu zapisuje serie powtorzenia jednostke i notatke`() = runTest {
        val planId = repository.createPlan(PlanDraft(name = "Plan"))
        val dayId = repository.addDay(planId, PlanDayDraft(name = "A"))
        val entry = repository.addExercise(dayId, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["squat"])))

        repository.updateTargets(
            entry,
            PlanExerciseTargets(
                sets = 3,
                repsMin = 60,
                repsMax = 60,
                repsUnit = RepsUnit.SECONDS,
                weightKg = 20.0,
                restSeconds = 90,
                note = "  na czas  ",
            ),
        )

        val exercise = requireNotNull(repository.observeDay(dayId).first()).exercises.single()
        assertEquals(3, exercise.sets)
        assertEquals(RepsUnit.SECONDS, exercise.repsUnit)
        assertEquals(20.0, exercise.weightKg!!, 0.001)
        assertEquals("na czas", exercise.note)
    }

    @Test
    fun `usuniecie planu zabiera dni i cwiczenia`() = runTest {
        val planId = repository.createPlan(PlanDraft(name = "Plan"))
        val dayId = repository.addDay(planId, PlanDayDraft(name = "A"))
        repository.addExercise(dayId, PlanExerciseDraft(exerciseId = requireNotNull(exerciseIds["squat"])))

        repository.deletePlan(planId)

        assertNull(repository.observePlanDetails(planId).first())
        assertNull(repository.observeDay(dayId).first())
        assertTrue(database.planDao().getDayExercises(dayId).isEmpty())
    }
}
