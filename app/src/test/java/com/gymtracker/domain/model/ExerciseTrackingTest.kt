package com.gymtracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typ zapisu serii rozstrzyga dwie rzeczy: jakie pola pokazać i czy serię
 * da się zapisać. Test pilnuje obu, bo rozjazd kończy się serią bez wartości
 * albo formularzem, który pyta o ciężar przy pompkach.
 */
class ExerciseTrackingTest {

    @Test
    fun `domyslny typ zalezy od sprzetu`() {
        assertEquals(ExerciseTracking.REPS, ExerciseTracking.defaultFor("Bodyweight"))
        assertEquals(ExerciseTracking.WEIGHT_REPS, ExerciseTracking.defaultFor("Barbell"))
        assertEquals(ExerciseTracking.WEIGHT_REPS, ExerciseTracking.defaultFor(null))
    }

    @Test
    fun `nieznana wartosc z bazy wraca do ciezar plus powtorzenia`() {
        assertEquals(ExerciseTracking.WEIGHT_REPS, ExerciseTracking.fromWire(null))
        assertEquals(ExerciseTracking.WEIGHT_REPS, ExerciseTracking.fromWire("nie_ma_takiego"))
        assertEquals(ExerciseTracking.TIME, ExerciseTracking.fromWire("time"))
    }

    @Test
    fun `jednostka planu wygrywa dla wartosci serii`() {
        assertEquals(ExerciseTracking.TIME, ExerciseTracking.WEIGHT_REPS.forTarget(RepsUnit.SECONDS))
        assertEquals(ExerciseTracking.TIME, ExerciseTracking.REPS.forTarget(RepsUnit.SECONDS))
        assertEquals(
            "czas i powtórzenia zostają, gdy plan też liczy czas",
            ExerciseTracking.TIME_REPS,
            ExerciseTracking.TIME_REPS.forTarget(RepsUnit.SECONDS),
        )
        assertEquals(ExerciseTracking.REPS, ExerciseTracking.TIME.forTarget(RepsUnit.REPS))
        assertEquals(ExerciseTracking.WEIGHT_REPS, ExerciseTracking.WEIGHT_REPS.forTarget(RepsUnit.REPS))
        assertEquals(ExerciseTracking.DISTANCE, ExerciseTracking.WEIGHT_REPS.forTarget(RepsUnit.METERS))
        assertEquals(ExerciseTracking.REPS, ExerciseTracking.DISTANCE.forTarget(RepsUnit.REPS))
    }

    @Test
    fun `seria jest kompletna tylko z wartoscia wymagana przez typ`() {
        assertTrue(SetLogDraft(weightKg = 100.0, reps = 5).isCompleteFor(ExerciseTracking.WEIGHT_REPS))
        assertTrue("ciężar jest opcjonalny", SetLogDraft(reps = 8).isCompleteFor(ExerciseTracking.WEIGHT_REPS))
        assertFalse("same powtórzenia nie wystarczą, gdy typ pyta o czas", SetLogDraft(reps = 8).isCompleteFor(ExerciseTracking.TIME))
        assertTrue(SetLogDraft(durationS = 60).isCompleteFor(ExerciseTracking.TIME))
        assertFalse(SetLogDraft(durationS = 0).isCompleteFor(ExerciseTracking.TIME))
        assertTrue(SetLogDraft(durationS = 60, reps = 10).isCompleteFor(ExerciseTracking.TIME_REPS))
        assertFalse(SetLogDraft(durationS = 60).isCompleteFor(ExerciseTracking.TIME_REPS))
        assertTrue(SetLogDraft(reps = 20).isCompleteFor(ExerciseTracking.REPS))
        assertTrue(SetLogDraft(reps = 30).isCompleteFor(ExerciseTracking.DISTANCE))
        assertFalse("dystans bez metrów", SetLogDraft(weightKg = 20.0).isCompleteFor(ExerciseTracking.DISTANCE))
    }
}
