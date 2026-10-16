package com.gymtracker.ui.calendar

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Siatka miesiąca i formatowanie dat. Siatka jest czystą funkcją, więc
 * sprawdzamy dokładnie układ: tydzień od poniedziałku, pełne wiersze i brzegi
 * sąsiednich miesięcy.
 *
 * Uwaga: nazwy miesięcy i dni pochodzą z danych językowych (CLDR), więc
 * porównujemy je tylko tam, gdzie zapis jest pewny - samo „czy data trafia na
 * właściwy dzień” sprawdzamy na [LocalDate], bez tekstu.
 */
class CalendarFormatTest {

    private val polish = Locale.forLanguageTag("pl")
    private val english = Locale.forLanguageTag("en")

    @Test
    fun `pazdziernik 2026 zaczyna sie w czwartek wiec siatka cofa sie do 28 wrzesnia`() {
        val cells = monthGrid(YearMonth.of(2026, 10))

        assertEquals("poniedziałek na początku", LocalDate.of(2026, 9, 28), cells.first().date)
        assertEquals(35, cells.size)
        assertEquals(5, cells.size / 7)
        assertEquals(LocalDate.of(2026, 9, 28), cells[0].date)
        assertEquals(LocalDate.of(2026, 10, 1), cells[3].date)
        assertEquals(LocalDate.of(2026, 10, 31), cells[33].date)
        assertEquals(LocalDate.of(2026, 11, 1), cells[34].date)

        assertFalse("28 września to jeszcze wrzesień", cells[0].inMonth)
        assertTrue(cells[3].inMonth)
        assertTrue(cells[33].inMonth)
        assertFalse("1 listopada to już listopad", cells[34].inMonth)
    }

    @Test
    fun `kazdy wiersz siatki zaczyna sie w poniedziałek`() {
        listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 8), YearMonth.of(2026, 2)).forEach { month ->
            monthGrid(month).chunked(7).forEach { week ->
                assertEquals("tydzień w $month", java.time.DayOfWeek.MONDAY, week.first().date.dayOfWeek)
                assertEquals(7, week.size)
            }
        }
    }

    @Test
    fun `miesiac bez pelnych tygodni jest dopelniony`() {
        // Luty 2026: 28 dni, 1 lutego wypada w niedzielę - do przodu brakuje 6 dni.
        val february = monthGrid(YearMonth.of(2026, 2))

        assertEquals(35, february.size)
        assertEquals(LocalDate.of(2026, 1, 26), february.first().date)
        assertEquals(LocalDate.of(2026, 3, 1), february.last().date)
        assertEquals(28, february.count { cell -> cell.inMonth })
    }

    @Test
    fun `sierpien 2026 miesci sie w szesciu wierszach`() {
        val august = monthGrid(YearMonth.of(2026, 8))

        assertEquals(42, august.size)
        assertEquals(6, august.size / 7)
        assertEquals(31, august.count { cell -> cell.inMonth })
    }

    @Test
    fun `naglowek miesiaca jest w jezyku interfejsu`() {
        val october = YearMonth.of(2026, 10)

        assertEquals("October 2026", october.headerText(english))
        assertTrue(
            "polski nagłówek: ${october.headerText(polish)}",
            october.headerText(polish).contains("2026"),
        )
        assertTrue(
            "polski nagłówek miesiąca",
            october.headerText(polish).startsWith("październik"),
        )
    }

    @Test
    fun `krotkie daty bez roku i z rokiem`() {
        val date = LocalDate.of(2026, 10, 1)

        assertEquals("1.10.2026", date.isoText())
        assertEquals("5.03.2027", LocalDate.of(2027, 3, 5).isoText())
        assertEquals("czw., 1 paź", date.shortDayText(polish))
        assertEquals("czwartek", date.weekdayText(polish))
        assertEquals("1 października", date.dayMonthText(polish))
    }

    @Test
    fun `data z DatePicker wraca ta sama - takze przy zmianie czasu`() {
        // Wybór z material3 to północ w UTC; czytanie w strefie urządzenia
        // przesunęłoby dzień o jeden na zachód od Greenwich.
        val dates = listOf(
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 3, 29), // zmiana czasu w Polsce
            LocalDate.of(2026, 12, 31),
        )

        dates.forEach { date ->
            assertEquals(date, utcMillisToDate(date.toUtcMillis()))
        }
    }

    @Test
    fun `koniec miesiaca i poczatek roku tez sie zgadzaja`() {
        val newYear = LocalDate.of(2027, 1, 1)

        assertEquals("1.01.2027", newYear.isoText())
        assertEquals(newYear, utcMillisToDate(newYear.toUtcMillis()))
        assertEquals(LocalDate.of(2026, 12, 31), newYear.minusDays(1))
    }
}
