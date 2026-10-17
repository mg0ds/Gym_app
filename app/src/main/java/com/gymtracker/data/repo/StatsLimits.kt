package com.gymtracker.data.repo

/**
 * Ile najnowszych wierszy wciągamy do statystyk.
 *
 * Statystyki liczymy w Kotlinie (średnie, mediana, 1RM), więc potrzebujemy
 * wierszy, a nie agregatów - stąd limity. Jeden wspólny limit dla przerw
 * oznacza, że karta ćwiczenia, historia treningu i wykres postępu patrzą na
 * ten sam zbiór danych i nie mogą pokazać trzech różnych średnich.
 */
internal object StatsLimits {

    /**
     * Przerwy pod średnie. 1000 przerw to kilka lat treningu; starsze i tak
     * nie zmienią średniej w sposób widoczny na ekranie.
     */
    const val REST_ROWS = 1_000

    /**
     * Serie jednego ćwiczenia pod wykres i rekordy. Jedno ćwiczenie robi
     * kilka serii na trening, więc limit jest wyższy niż przy przerwach -
     * ma objąć naprawdę długą historię, zanim zacznie obcinać.
     */
    const val EXERCISE_SET_ROWS = 3_000
}
