package com.gymtracker.ui.session

import org.junit.Assert.assertEquals
import org.junit.Test

/** Format czasu treningu - liczba, którą użytkownik widzi przez cały trening. */
class SessionFormatTest {

    @Test
    fun `krótki trening to minuty i sekundy`() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:07", formatDuration(7_000L))
        assertEquals("12:34", formatDuration(12 * 60_000L + 34_000L))
        assertEquals("59:59", formatDuration(59 * 60_000L + 59_000L))
    }

    @Test
    fun `od godziny dochodzi czesc godzinowa`() {
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("1:05:12", formatDuration(3_600_000L + 5 * 60_000L + 12_000L))
        assertEquals("12:00:01", formatDuration(12 * 3_600_000L + 1_000L))
    }

    @Test
    fun `niepelne sekundy zaokraglaja sie w dol`() {
        assertEquals("0:01", formatDuration(1_999L))
    }
}
