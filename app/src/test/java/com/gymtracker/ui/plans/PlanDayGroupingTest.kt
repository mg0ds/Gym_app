package com.gymtracker.ui.plans

import com.gymtracker.domain.model.PlanExercise
import com.gymtracker.domain.model.RepsUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Grupowanie ćwiczeń dnia to logika, która decyduje, jak wygląda lista i co
 * idzie do bazy przy przeciąganiu - dlatego testujemy ją bez Androida.
 */
class PlanDayGroupingTest {

    @Test
    fun `alternatywa trafia pod swoje cwiczenie podstawowe`() {
        val groups = listOf(
            exercise(id = 1, exerciseId = 10, name = "Squat", position = 1),
            exercise(id = 2, exerciseId = 11, name = "Bench", position = 2, group = "10-or-12", isDefault = true),
            exercise(id = 3, exerciseId = 12, name = "Press", position = 3, group = "10-or-12", isDefault = false),
            exercise(id = 4, exerciseId = 13, name = "Row", position = 4),
        ).toGroups()

        assertEquals(3, groups.size)
        assertEquals("Squat", groups[0].primary.exerciseName)
        assertEquals("Bench", groups[1].primary.exerciseName)
        assertEquals(listOf("Press"), groups[1].alternatives.map { it.exerciseName })
        assertEquals("Row", groups[2].primary.exerciseName)
        assertEquals(emptyList<String>(), groups[2].alternatives.map { it.exerciseName })
    }

    @Test
    fun `kolejnosc cwiczen podstawowych idzie za pozycja`() {
        val groups = listOf(
            exercise(id = 5, exerciseId = 1, name = "Trzecie", position = 3),
            exercise(id = 6, exerciseId = 2, name = "Pierwsze", position = 1),
            exercise(id = 7, exerciseId = 3, name = "Drugie", position = 2),
        ).toGroups()

        assertEquals(listOf("Pierwsze", "Drugie", "Trzecie"), groups.map { it.primary.exerciseName })
    }

    @Test
    fun `alternatywa bez swojego cwiczenia nie ginie`() {
        // Stan, który nie powinien się zdarzyć, ale gdy się zdarzy - lepiej
        // pokazać ćwiczenie osobno niż zgubić je z planu.
        val groups = listOf(
            exercise(id = 1, exerciseId = 10, name = "Squat", position = 1),
            exercise(id = 2, exerciseId = 11, name = "Osierocona", position = 2, group = "10-or-11", isDefault = false),
        ).toGroups()

        assertEquals(listOf("Squat", "Osierocona"), groups.map { it.primary.exerciseName })
        assertEquals(emptyList<String>(), groups[1].alternatives.map { it.exerciseName })
    }

    private fun exercise(
        id: Long,
        exerciseId: Long,
        name: String,
        position: Int,
        group: String? = null,
        isDefault: Boolean = true,
    ): PlanExercise = PlanExercise(
        id = id,
        exerciseId = exerciseId,
        exerciseName = name,
        muscleGroup = "Test",
        equipment = "Barbell",
        position = position,
        sets = 5,
        repsMin = 5,
        repsMax = 5,
        repsUnit = RepsUnit.REPS,
        weightKg = null,
        restSeconds = 90,
        supersetGroup = null,
        alternativeGroup = group,
        isDefaultChoice = isDefault,
        note = null,
    )
}
