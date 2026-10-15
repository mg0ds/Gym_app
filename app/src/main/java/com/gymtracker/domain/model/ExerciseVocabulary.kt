package com.gymtracker.domain.model

/**
 * Słownik wartości, jakich używamy w katalogu. Klucze trzymamy w jednym języku
 * (takim, jaki przyszedł w danych startowych), a etykiety tłumaczymy dopiero
 * w warstwie UI - dzięki temu import, filtry i wyszukiwanie mówią jednym
 * słownikiem, niezależnie od języka interfejsu.
 */
object ExerciseVocabulary {

    /** Partie mięśniowe z danych startowych + kilka typowych dla własnych ćwiczeń. */
    val muscleGroups: List<String> = listOf(
        "Chest",
        "Lats",
        "Upper Back",
        "Lower Back",
        "Traps",
        "Shoulders",
        "Biceps",
        "Triceps",
        "Forearms",
        "Abs",
        "Glutes",
        "Quads",
        "Hamstrings",
        "Calves",
        "Cardio",
        "Other",
    )

    val equipment: List<String> = listOf(
        "Barbell",
        "Dumbbell",
        "EZ Bar",
        "Cable",
        "Machine",
        "Bodyweight",
        "Kettlebell",
        "Bands",
        "Other",
    )

    val mechanics: List<String> = listOf("Compound", "Isolation")

    val levels: List<String> = listOf("Beginner", "Intermediate", "Advanced")
}
