package com.gymtracker.ui.plans

import org.junit.Assert.assertEquals
import org.junit.Test

/** Przestawianie wiersza - podstawa przeciągania i menu „wyżej/niżej”. */
class ReorderTest {

    private val items = listOf("A", "B", "C", "D")

    @Test
    fun `przesuniecie w dol wstawia element za cel`() {
        assertEquals(listOf("B", "C", "A", "D"), items.moveItem(from = 0, to = 2))
    }

    @Test
    fun `przesuniecie w gore wstawia element przed cel`() {
        assertEquals(listOf("A", "D", "B", "C"), items.moveItem(from = 3, to = 1))
    }

    @Test
    fun `ten sam indeks niczego nie zmienia`() {
        assertEquals(items, items.moveItem(from = 2, to = 2))
    }

    @Test
    fun `indeks poza zakresem niczego nie psuje`() {
        assertEquals(items, items.moveItem(from = 0, to = 9))
        assertEquals(items, items.moveItem(from = -1, to = 1))
        assertEquals(items, items.moveItem(from = 9, to = 1))
    }

    @Test
    fun `przesuniecie o jeden dziala w obie strony`() {
        assertEquals(listOf("B", "A", "C", "D"), items.moveItem(from = 0, to = 1))
        assertEquals(listOf("A", "C", "B", "D"), items.moveItem(from = 2, to = 1))
    }
}
