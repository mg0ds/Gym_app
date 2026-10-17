package com.gymtracker.domain.model

/**
 * Kroki wykonania ćwiczenia - instrukcja „jak to zrobić”.
 *
 * To **nie** jest komentarz użytkownika (`Exercise.notes`) - kroki to technika:
 * „ustaw sztangę na wysokości klatki”, „ściągnij łopatki”. W bazie leżą jako
 * JSON (tak samo jak mięśnie dodatkowe), a w formularzu jako zwykły tekst:
 * **jedna linia to jeden krok**. To wybór celowy - kroki dyktuje się liniami,
 * a nie klika „dodaj krok” pięć razy.
 *
 * Numerację z początku linii odcinamy: listę numerujemy sami przy wyświetlaniu,
 * więc „1. Ustaw sztangę” i „Ustaw sztangę” to ten sam krok, a użytkownik może
 * wkleić instrukcję z numerami bez sprzątania jej ręcznie.
 */
object InstructionSteps {

    /** Numeracja z początku linii: `1.`, `2)`, `10.` - z opcjonalnym wcięciem. */
    private val LEADING_NUMBER = Regex("""^\s*\d{1,2}\s*[.)]\s+""")

    /** Jedna linia = jeden krok; puste linie wypadają, numeracja znika. */
    fun normalize(steps: List<String>): List<String> = steps
        .map { step -> step.trim().replace(LEADING_NUMBER, "").trim() }
        .filter { step -> step.isNotEmpty() }

    /** Tekst z formularza na listę kroków. */
    fun fromText(text: String?): List<String> = normalize(text.orEmpty().lines())

    /** Lista kroków z powrotem do formularza (jedna linia = jeden krok). */
    fun toText(steps: List<String>): String = steps.joinToString(separator = "\n")
}
