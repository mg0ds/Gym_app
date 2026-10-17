package com.gymtracker.domain.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arytmetyka przerwy. Wszystko sprowadza się do jednego: koniec przerwy jest
 * **liczony** z początku i celu, więc „+15 s” nie potrzebuje własnego pola,
 * a po restarcie procesu odliczanie wstaje w tym samym miejscu.
 */
class RestTimerStateTest {

    @Test
    fun `koniec przerwy wynika z poczatku i celu`() {
        val rest = rest(plannedSeconds = 90, startedAt = 10_000L)

        assertEquals(100_000L, rest.endsAtEpochMs)
    }

    @Test
    fun `pozostaly czas nie schodzi ponizej zera`() {
        val rest = rest(plannedSeconds = 90, startedAt = 10_000L)

        assertEquals(50_000L, rest.remainingMs(50_000L))
        assertEquals(0L, rest.remainingMs(150_000L))
    }

    @Test
    fun `cel mija dokladnie na koncu`() {
        val rest = rest(plannedSeconds = 60, startedAt = 0L)

        assertFalse(rest.isOver(59_999L))
        assertTrue(rest.isOver(60_000L))
    }

    @Test
    fun `wydluzenie przesuwa koniec i znaczy powod extended`() {
        val rest = rest(plannedSeconds = 60, startedAt = 0L).extendedBy(15)

        assertEquals(75, rest.plannedSeconds)
        assertEquals(75_000L, rest.endsAtEpochMs)
        assertTrue(rest.extended)
    }

    @Test
    fun `skrocenie nie zejdzie ponizej minimum`() {
        val rest = rest(plannedSeconds = 20, startedAt = 0L)
            .shrunkBy(15)
            .shrunkBy(15)

        assertEquals(ActiveRest.MIN_PLANNED_SECONDS, rest.plannedSeconds)
    }

    @Test
    fun `krok zerowy nic nie zmienia`() {
        val rest = rest(plannedSeconds = 60, startedAt = 0L)

        assertEquals(rest, rest.extendedBy(0))
        assertEquals(rest, rest.shrunkBy(0))
    }

    private fun rest(plannedSeconds: Int, startedAt: Long): ActiveRest = ActiveRest(
        intervalId = 1L,
        sessionId = 2L,
        exerciseId = 3L,
        exerciseName = "Barbell Back Squat",
        plannedSeconds = plannedSeconds,
        startedAtEpochMs = startedAt,
    )
}
