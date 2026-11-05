package com.gymtracker.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Osie wykresów postępu.
 *
 * Pilnuje tego, co widać dopiero na ekranie: etykiety mają wypadać na pełnych
 * kilogramach, a nie na surowym minimum danych. Automatyczny zakres Vico dawał
 * przy jednym ciężarze 40 kg etykiety 32, 42, 52…; tutaj sprawdzamy, że nasz
 * plan daje 35, 40, 45.
 */
class ProgressChartAxisTest {

    @Test
    fun `jeden ciężar 40 kg daje pelne etykiety wokol wartosci`() {
        val axis = yAxisPlan(listOf(40.0, 40.0, 40.0), minStep = 5.0)

        assertEquals(35.0, axis.minY, 0.0001)
        assertEquals(45.0, axis.maxY, 0.0001)
        assertEquals(5.0, axis.step, 0.0001)
    }

    @Test
    fun `zakres 45-117 kg daje krok 20 i nie schodzi do zera`() {
        // Rampa 45–100 kg plus szacowany 1RM 117 kg: dolna etykieta to 40,
        // nie 20 ani 0.
        val axis = yAxisPlan(listOf(45.0, 100.0, 117.0), minStep = 5.0)

        assertEquals(40.0, axis.minY, 0.0001)
        assertEquals(120.0, axis.maxY, 0.0001)
        assertEquals(20.0, axis.step, 0.0001)
    }

    @Test
    fun `dane zawsze mieszcza sie w zakresie`() {
        val cases = listOf(
            listOf(2.5) to 5.0,
            listOf(40.0) to 5.0,
            listOf(45.0, 100.0, 117.0) to 5.0,
            listOf(6275.0, 6950.0) to 0.0,
            listOf(0.0, 0.0) to 0.0,
        )

        cases.forEach { (values, minStep) ->
            val axis = yAxisPlan(values, minStep = minStep)
            val low = values.min()
            val high = values.max()
            assertTrue("dolna krawędź $low w $axis", axis.minY <= low)
            assertTrue("górna krawędź $high w $axis", axis.maxY >= high)
            assertTrue("zakres bez rozpiętości: $axis", axis.maxY > axis.minY)
            assertTrue("krok nie dodatni: $axis", axis.step > 0.0)
        }
    }

    @Test
    fun `nieujemne dane nie dostaja ujemnej osi`() {
        val axis = yAxisPlan(listOf(0.0, 2.0), minStep = 5.0)

        assertEquals(0.0, axis.minY, 0.0001)
        assertTrue("górna krawędź nad danymi: $axis", axis.maxY >= 2.0)
    }

    @Test
    fun `krok jest wielokrotnoscia 1 2 5 razy potega dziesiatki`() {
        val cases = listOf(
            yAxisPlan(listOf(40.0, 40.0), minStep = 5.0),
            yAxisPlan(listOf(45.0, 117.0), minStep = 5.0),
            yAxisPlan(listOf(6275.0, 6950.0)),
        )

        cases.forEach { axis ->
            val magnitude = Math.pow(10.0, Math.floor(Math.log10(axis.step)))
            val normalized = axis.step / magnitude
            assertTrue(
                "krok ${axis.step} nie jest „ładny”",
                normalized == 1.0 || normalized == 2.0 || normalized == 5.0,
            )
        }
    }
}
