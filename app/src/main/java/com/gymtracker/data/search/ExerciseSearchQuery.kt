package com.gymtracker.data.search

/**
 * Zamienia to, co użytkownik wpisał w wyszukiwarce, na zapytanie dla FTS.
 *
 * Zasady:
 *  * wyciągamy z tekstu tylko litery i cyfry (reszta to separatory), więc
 *    żadne cudzysłowy, gwiazdki czy minusy od użytkownika nie zepsują zapytania
 *    ani nie zmienią jego znaczenia,
 *  * każdy wyraz szukamy jako **prefiks** (`sztan*` znajdzie „Sztanga”,
 *    „Sztangą” i „Sztangi” - dopisywanie końcówek przestaje przeszkadzać),
 *  * kolejne wyrazy łączymy spójnikiem AND (domyślne zachowanie FTS), czyli
 *    „bench press” zawęża wynik, a nie rozszerza,
 *  * brak sensownych wyrazów (np. sam znak zapytania) → `null`, czyli brak
 *    filtrowania - lista pokazuje wszystko.
 *
 * Diakrytyki (ą, ć, ę, ł, ń, ó, ś, ź, ż) usuwa już tokenizer FTS, więc
 * „obwod” i „obwód” trafiają w ten sam wpis.
 */
object ExerciseSearchQuery {

    /** Litery (w tym polskie) i cyfry - wszystko inne traktujemy jako separator. */
    private val TOKEN = Regex("[\\p{L}\\p{N}]+")

    fun build(rawQuery: String?): String? {
        val tokens = rawQuery?.let { TOKEN.findAll(it).map { match -> match.value }.toList() }.orEmpty()
        if (tokens.isEmpty()) return null
        return tokens.joinToString(separator = " ") { token -> "$token*" }
    }
}
