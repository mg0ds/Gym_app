package com.gymtracker.ui.body

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Osie wartości na wykresie karty postaci.
 *
 * Pilnuje rzeczy, których nie widać w kodzie, a widać na ekranie: zakres nie
 * startuje od zera (waga 82.5-84 kg od zera to linia prosta przy górnej
 * krawędzi), krok etykiet jest „okrągły”, a pomiar zawsze leży w zakresie.
 */
class BodyChartRangeTest {

    @Test
    fun `zakres nie startuje od zera`() {
        val axis = bodyValueAxis(82.5, 84.0)

        assertTrue("zero odcina cały wykres", axis.range.start > 0.0)
        assertEquals(0.5, axis.step, 0.0001)
        assertEquals(82.5, axis.range.start, 0.0001)
        assertEquals(84.0, axis.range.endInclusive, 0.0001)
    }

    @Test
    fun `krok jest okragly, a zakres przyciety do niego`() {
        // 70-200 cm: krok 50, żeby etykiety były „70, 100, 150, 200”, a nie „70, 120, 170”.
        val axis = bodyValueAxis(70.0, 200.0)

        assertEquals(50.0, axis.step, 0.0001)
        assertEquals(50.0, axis.range.start, 0.0001)
        assertEquals(200.0, axis.range.endInclusive, 0.0001)
    }

    @Test
    fun `pomiary zawsze mieszcza sie w zakresie`() {
        val cases = listOf(
            82.5 to 84.0,
            70.0 to 200.0,
            39.9 to 40.3,
            -3.0 to 1.5,
            1000.0 to 1001.0,
        )

        cases.forEach { (low, high) ->
            val axis = bodyValueAxis(low, high)
            assertTrue("dolna krawędź $low w $axis", axis.range.start <= low)
            assertTrue("górna krawędź $high w $axis", axis.range.endInclusive >= high)
            assertTrue("zakres bez rozpiętości: $axis", axis.range.endInclusive > axis.range.start)
            assertTrue("krok nie dodatni: $axis", axis.step > 0.0)
        }
    }

    @Test
    fun `kolejnosc argumentow nie ma znaczenia`() {
        assertEquals(bodyValueAxis(82.5, 84.0), bodyValueAxis(84.0, 82.5))
    }

    @Test
    fun `rowne pomiary dostaja rozciaglosc wokol wartosci`() {
        // Dwa pomiary tego samego dnia albo powtórzona waga - nie ma czego rozciągać.
        val axis = bodyValueAxis(82.0, 82.0)

        assertEquals(81.5, axis.range.start, 0.0001)
        assertEquals(82.5, axis.range.endInclusive, 0.0001)
        assertEquals("pomiar zostaje na środku", 82.0, (axis.range.start + 82.5) / 2, 0.0001)
    }

    @Test
    fun `maly wskaznik tez dostaje czytelne etykiety`() {
        // Obwód w centymetrach z dokładnością do milimetra: krok 0.1, pięć etykiet.
        val axis = bodyValueAxis(39.9, 40.3)

        assertEquals(0.1, axis.step, 0.0001)
        assertEquals(5, ((axis.range.endInclusive - axis.range.start) / axis.step).roundToInt() + 1)
    }
}
