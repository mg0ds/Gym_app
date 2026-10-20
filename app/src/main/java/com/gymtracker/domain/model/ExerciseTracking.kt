package com.gymtracker.domain.model

/**
 * Co zapisujemy w serii danego ćwiczenia.
 *
 * Typ należy do **katalogu ćwiczenia** (a nie do planu): pompki są zawsze
 * „tylko powtórzenia”, a bieżnia „tylko czas”, niezależnie od tego, w jakim
 * planie się pojawią. Dzięki temu formularz serii pokazuje tylko te pola,
 * które mają sens, i nie trzeba za każdym razem ukrywać ciężaru ręcznie.
 *
 * [unit] mówi, jak pokazywać cel i podsumowanie serii; przy
 * [TIME_REPS] wartością główną jest czas, a powtórzenia są dodatkiem.
 * [usesWeight] decyduje, czy w ogóle pytać o ciężar.
 */
enum class ExerciseTracking(val wire: String) {
    /** Sztanga, hantle, maszyny - ciężar i powtórzenia. */
    WEIGHT_REPS("weight_reps"),

    /** Pompki, podciąganie, brzuszki - tylko liczba powtórzeń. */
    REPS("reps"),

    /** Deska, bieżnia, orbitrek - tylko czas. */
    TIME("time"),

    /** Ćwiczenia łączące czas i powtórzenia (np. deska z unoszeniem nóg). */
    TIME_REPS("time_reps"),

    /** Sledź, farmer's walk - dystans w metrach. */
    DISTANCE("distance"),
    ;

    /** Jednostka celu i podsumowania serii. */
    val unit: RepsUnit
        get() = when (this) {
            WEIGHT_REPS, REPS -> RepsUnit.REPS
            TIME, TIME_REPS -> RepsUnit.SECONDS
            DISTANCE -> RepsUnit.METERS
        }

    /** Czy formularz serii pokazuje ciężar. */
    val usesWeight: Boolean get() = this == WEIGHT_REPS

    /** Czy seria ma wartość „powtórzenia” (przy dystansie: metry). */
    val usesCount: Boolean
        get() = this == WEIGHT_REPS || this == REPS || this == TIME_REPS || this == DISTANCE

    /** Czy seria ma wartość „czas”. */
    val usesDuration: Boolean get() = this == TIME || this == TIME_REPS

    /**
     * Typ zapisu dopasowany do jednostki celu z planu.
     *
     * Katalog mówi, jak ćwiczenie zwykle się zapisuje, ale plan może to
     * nadpisać (np. ten sam plank raz na czas, raz na powtórzenia). Jednostka
     * z planu wygrywa dla **wartości serii**, a typ katalogowy decyduje tylko
     * wtedy, gdy nie przeczy jej wprost.
     */
    fun forTarget(unit: RepsUnit): ExerciseTracking = when (unit) {
        RepsUnit.REPS -> if (usesWeight) WEIGHT_REPS else REPS
        RepsUnit.SECONDS -> if (this == TIME_REPS) TIME_REPS else TIME
        RepsUnit.METERS -> DISTANCE
    }

    companion object {
        fun fromWire(value: String?): ExerciseTracking =
            entries.firstOrNull { tracking -> tracking.wire == value } ?: WEIGHT_REPS

        /**
         * Domyślny typ dla nowego ćwiczenia: sprzęt „Bodyweight” nie pyta
         * o ciężar, bo najczęściej go nie ma. Użytkownik może to zmienić.
         */
        fun defaultFor(equipment: String?): ExerciseTracking =
            if (equipment == "Bodyweight") REPS else WEIGHT_REPS
    }
}
