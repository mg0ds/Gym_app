package com.gymtracker.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Para przełączników (dźwięk, wibracja) wybiera kanał powiadomień.
 *
 * To nie kosmetyka: kanałów nie da się przestawić po utworzeniu, więc cztery
 * kombinacje muszą dać cztery różne identyfikatory. Pilnujemy też, że para
 * „dźwięk + wibracja” trafia w identyfikator z M5 - inaczej aktualizacja
 * zgubiłaby ustawienia kanału, które użytkownik już ma w systemie.
 */
class RestAlertChannelTest {

    @Test
    fun `domyslna para trzyma sie kanalu z M5`() {
        val channel = restAlertChannel(sound = true, vibration = true)

        assertEquals(LEGACY_CHANNEL_ID, channel.id)
        assertTrue(channel.sound)
        assertTrue(channel.vibration)
    }

    @Test
    fun `cztery kombinacje maja cztery rozne kanaly`() {
        val ids = listOf(
            restAlertChannel(sound = true, vibration = true),
            restAlertChannel(sound = true, vibration = false),
            restAlertChannel(sound = false, vibration = true),
            restAlertChannel(sound = false, vibration = false),
        ).map { channel -> channel.id }

        assertEquals("identyfikatory kanałów muszą się różnić", 4, ids.toSet().size)
        assertNotEquals(ids[0], ids[1])
    }

    @Test
    fun `kanal mowi co robi`() {
        val soundOnly = restAlertChannel(sound = true, vibration = false)
        assertTrue(soundOnly.sound)
        assertFalse(soundOnly.vibration)

        val vibrationOnly = restAlertChannel(sound = false, vibration = true)
        assertFalse(vibrationOnly.sound)
        assertTrue(vibrationOnly.vibration)

        val silent = restAlertChannel(sound = false, vibration = false)
        assertFalse(silent.sound)
        assertFalse(silent.vibration)

        val names = listOf(soundOnly, vibrationOnly, silent).map { channel -> channel.nameRes }
        assertEquals("każdy kanał ma własną nazwę w ustawieniach systemu", 3, names.toSet().size)
    }
}
