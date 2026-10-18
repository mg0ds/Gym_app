package com.gymtracker.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Wiersz „Język aplikacji” w Ustawieniach.
 *
 * Wybór języka dla aplikacji jest systemowy, więc testujemy trzy rzeczy: od
 * której wersji Androida wiersz w ogóle wchodzi w grę, czy system **naprawdę**
 * ma ten ekran (sama wersja nie wystarcza) i czy zamiar celuje w tę aplikację -
 * inaczej otworzylibyśmy ustawienia bez wskazania pakietu.
 */
@RunWith(RobolectricTestRunner::class)
class AppLanguageSettingsTest {

    @Test
    fun `wybor jezyka jest dostepny od Androida 13`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(appLanguageSettingsAvailable(context, sdkInt = 26))
        assertFalse(appLanguageSettingsAvailable(context, sdkInt = 32))
    }

    @Test
    fun `bez ekranu w systemie wiersz nie udaje akcji`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Robolectric nie ma systemowych Ustawień, więc to dokładnie ten
        // przypadek, którego nie widzi sam warunek na wersję Androida.
        assertFalse(appLanguageSettingsAvailable(context, sdkInt = 33))
    }

    @Test
    fun `gdy system ma ekran, wiersz dziala`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).addResolveInfoForIntent(
            appLanguageSettingsIntent(context.packageName),
            resolveInfo(),
        )

        assertTrue(appLanguageSettingsAvailable(context, sdkInt = 33))
        assertTrue(appLanguageSettingsAvailable(context, sdkInt = 36))
    }

    @Test
    fun `zamiar wskazuje pakiet jako dane`() {
        val intent = appLanguageSettingsIntent("com.gymtracker")

        assertEquals(Settings.ACTION_APP_LOCALE_SETTINGS, intent.action)
        // Kontrakt `ACTION_APP_LOCALE_SETTINGS`: pakiet jedzie w danych
        // (`package:<nazwa>`), a nie w ekstra - ekstra system ignoruje.
        assertEquals("package:com.gymtracker", intent.data.toString())
        assertEquals(null, intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
    }

    private fun resolveInfo(): ResolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = "com.android.settings"
            name = "AppLocalePickerActivity"
        }
    }
}
