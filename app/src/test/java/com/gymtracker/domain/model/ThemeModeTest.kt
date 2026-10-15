package com.gymtracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `nieznana wartosc z pamieci wraca do domyslnej`() {
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromStorage("nieznany"))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromStorage(null))
    }

    @Test
    fun `kazdy tryb da sie zapisac i odczytac`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorage(mode.storageValue))
        }
    }
}
