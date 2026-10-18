package com.gymtracker.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tłumaczenia: polski jest językiem domyślnym zasobów, angielski leży w
 * `values-en`. Brakujący klucz w jednym z plików nie wywala kompilacji - po
 * prostu pokazuje tekst z drugiego języka - więc pilnuje tego test.
 */
class StringResourcesTest {

    @Test
    fun `polski i angielski maja ten sam zestaw kluczy`() {
        val polish = stringKeys(File("src/main/res/values/strings.xml"))
        val english = stringKeys(File("src/main/res/values-en/strings.xml"))

        assertTrue("nie wczytałem polskich tekstów", polish.size > 400)
        assertEquals("klucze tylko w polskim", emptySet<String>(), (polish - english).toSet())
        assertEquals("klucze tylko w angielskim", emptySet<String>(), (english - polish).toSet())
    }

    @Test
    fun `zaden klucz nie jest powtorzony`() {
        listOf("values", "values-en").forEach { directory ->
            val keys = stringKeys(File("src/main/res/$directory/strings.xml"))
            val duplicated = keys.groupingBy { key -> key }.eachCount()
                .filterValues { count -> count > 1 }
                .keys
            assertEquals("powtórzone klucze w $directory", emptySet<String>(), duplicated)
        }
    }

    @Test
    fun `teksty z procentem sa escapowane albo w formacie`() {
        // `stringResource` nie puszcza tekstu przez `String.format`, więc `%%`
        // widać na ekranie jako „%%” (błąd z M8). Każdy tekst z `%` musi mieć
        // argument (`%1$s`) - inaczej jest literałem, który wyszedł na ekran.
        listOf("values", "values-en").forEach { directory ->
            val file = File("src/main/res/$directory/strings.xml")
            val suspicious = Regex("""<string name="([^"]+)">([^<]*)</string>""")
                .findAll(file.readText())
                .filter { match -> "%" in match.groupValues[2] }
                .filterNot { match -> FORMAT_ARGUMENT.containsMatchIn(match.groupValues[2]) }
                .map { match -> match.groupValues[1] }
                .toList()

            assertEquals("procent bez argumentu w $directory", emptyList<String>(), suspicious)
        }
    }

    private companion object {
        /** Argument formatu: `%d`, `%1$s`, `%2$.1f`, `%%`. */
        val FORMAT_ARGUMENT = Regex("""%(\d+\$)?[ .#0-9]*[sdf]|%%""")
    }

    private fun stringKeys(file: File): List<String> {
        assertTrue("brak pliku ${file.absolutePath} (zły katalog roboczy testu)", file.isFile)
        return Regex("""<string name="([^"]+)"""")
            .findAll(file.readText())
            .map { match -> match.groupValues[1] }
            .toList()
    }
}
