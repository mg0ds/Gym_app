package com.gymtracker.domain.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Zegar treningu. Pilnujemy tu trzech rzeczy, które najłatwiej zepsuć:
 *
 *  * czas rośnie liniowo i nie zależy od tego, ile razy go odczytamy,
 *  * pauza **zatrzymuje** zegar, a wznowienie dolicza ją do sumy (raz),
 *  * po restarcie procesu zegar wstaje z zapisanych liczb - bez pamięci
 *    o tym, ile tykań się odbyło.
 */
class WorkoutTimerStateTest {

    private val start = 1_000_000L

    @Test
    fun `bez pauzy czas to roznica od startu`() {
        val timer = WorkoutTimerState(startedAtEpochMs = start)

        assertEquals(0L, timer.elapsedMs(start))
        assertEquals(60_000L, timer.elapsedMs(start + 60_000L))
        assertFalse(timer.isPaused)
    }

    @Test
    fun `pauza zatrzymuje czas na momencie jej rozpoczecia`() {
        val timer = WorkoutTimerState(startedAtEpochMs = start).paused(start + 30_000L)

        assertTrue(timer.isPaused)
        assertEquals(30_000L, timer.elapsedMs(start + 30_000L))
        // Minęła minuta, a zegar stoi - czas treningu się nie zmienia.
        assertEquals(30_000L, timer.elapsedMs(start + 90_000L))
    }

    @Test
    fun `wznowienie dolicza pauze do sumy`() {
        val timer = WorkoutTimerState(startedAtEpochMs = start)
            .paused(start + 30_000L)
            .resumed(start + 90_000L)

        assertFalse(timer.isPaused)
        assertEquals(60_000L, timer.pausedTotalMs)
        // Minuta treningu + minuta pauzy = dwie minuty od startu, ale czasu
        // treningu nadal jest minuta.
        assertEquals(60_000L, timer.elapsedMs(start + 120_000L))
    }

    @Test
    fun `powtorne wstrzymanie nie przesuwa poczatku pauzy, a wznowienie bez pauzy nic nie zmienia`() {
        val running = WorkoutTimerState(startedAtEpochMs = start)

        // Pauza zaczęła się raz i trwa - drugie wciśnięcie nie może jej przesunąć
        // (inaczej część pauzy wsiąkłaby w czas treningu).
        assertEquals(
            running.paused(start + 10_000L),
            running.paused(start + 10_000L).paused(start + 20_000L),
        )
        assertEquals(running, running.resumed(start + 20_000L))
    }

    @Test
    fun `dwie pauzy sumuja sie`() {
        val timer = WorkoutTimerState(startedAtEpochMs = start)
            .paused(start + 10_000L)
            .resumed(start + 40_000L) // 30 s pauzy
            .paused(start + 60_000L)
            .resumed(start + 100_000L) // 40 s pauzy

        assertEquals(70_000L, timer.pausedTotalMs)
        assertEquals(30_000L, timer.elapsedMs(start + 100_000L))
    }

    @Test
    fun `zegar wstaje z zapisanych liczb po restarcie procesu`() {
        // To są dokładnie te trzy liczby, które leżą w `sessions`.
        val restored = WorkoutTimerState(
            startedAtEpochMs = start,
            pausedTotalMs = 120_000L,
            pausedAtEpochMs = start + 600_000L,
        )

        assertTrue(restored.isPaused)
        assertEquals(480_000L, restored.elapsedMs(start + 900_000L))
    }

    @Test
    fun `czas nie schodzi ponizej zera`() {
        val timer = WorkoutTimerState(startedAtEpochMs = start, pausedTotalMs = 5_000L)

        assertEquals(0L, timer.elapsedMs(start))
    }

    @Test
    fun `krotki trening to minuty i sekundy`() {
        assertEquals("0:00", formatElapsed(0L))
        assertEquals("0:07", formatElapsed(7_000L))
        assertEquals("12:34", formatElapsed(12 * 60_000L + 34_000L))
        assertEquals("59:59", formatElapsed(59 * 60_000L + 59_000L))
    }

    @Test
    fun `od godziny dochodzi czesc godzinowa`() {
        assertEquals("1:00:00", formatElapsed(3_600_000L))
        assertEquals("1:05:12", formatElapsed(3_600_000L + 5 * 60_000L + 12_000L))
        assertEquals("12:00:01", formatElapsed(12 * 3_600_000L + 1_000L))
    }

    @Test
    fun `niepelne sekundy zaokraglaja sie w dol`() {
        assertEquals("0:01", formatElapsed(1_999L))
    }
}
