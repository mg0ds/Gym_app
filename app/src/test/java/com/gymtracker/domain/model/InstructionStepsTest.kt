package com.gymtracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kroki wykonania: normalizacja tekstu z formularza.
 *
 * Reguła „jedna linia = jeden krok” jest umową między formularzem a wyświetlaniem,
 * więc pilnujemy jej tutaj, a nie w UI.
 */
class InstructionStepsTest {

    @Test
    fun `kazda linia to jeden krok a puste linie wypadaja`() {
        val steps = InstructionSteps.fromText("Ściągnij łopatki\n\n   \nWyciśnij sztangę\n")

        assertEquals(listOf("Ściągnij łopatki", "Wyciśnij sztangę"), steps)
    }

    @Test
    fun `numeracja z poczatku linii znika bo numerujemy sami`() {
        val steps = InstructionSteps.fromText(
            "1. Ustaw sztangę\n2) Ściągnij łopatki\n10. Wyciśnij\nkontroluj tempo",
        )

        assertEquals(
            listOf("Ustaw sztangę", "Ściągnij łopatki", "Wyciśnij", "kontroluj tempo"),
            steps,
        )
    }

    @Test
    fun `liczba w tresci kroku zostaje`() {
        // „3 sekundy” to nie numeracja - odcinamy tylko numer z kropką lub nawiasem.
        val steps = InstructionSteps.fromText("Trzymaj 3 sekundy na dole")

        assertEquals(listOf("Trzymaj 3 sekundy na dole"), steps)
    }

    @Test
    fun `tekst i lista zamieniaja sie bez straty`() {
        val steps = listOf("Ustaw sztangę", "Wyciśnij")

        assertEquals(steps, InstructionSteps.fromText(InstructionSteps.toText(steps)))
    }

    @Test
    fun `normalizacja przycina spacje i nie robi pustej listy z pustego tekstu`() {
        assertTrue(InstructionSteps.normalize(listOf("   ", "", "  a  ")).none { it != "a" })
        assertTrue(InstructionSteps.fromText(null).isEmpty())
    }
}
