package com.gymtracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.gymtracker.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val file = File(temporaryFolder.newFolder(), "settings.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `domyslny motyw to zgodny z systemem`() = runTest {
        val settings = SettingsDataStore(dataStore)

        assertEquals(ThemeMode.SYSTEM, settings.themeMode.first())
    }

    @Test
    fun `zapisany motyw jest odczytywany`() = runTest {
        val settings = SettingsDataStore(dataStore)

        settings.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, settings.themeMode.first())
    }

    @Test
    fun `alarm Przerwy ma domyslnie i dzwiek, i wibracje`() = runTest {
        val settings = SettingsDataStore(dataStore)

        assertTrue(settings.restSoundEnabled.first())
        assertTrue(settings.restVibrationEnabled.first())
    }

    @Test
    fun `wylaczone skladowe alarmu sa zapamietywane`() = runTest {
        val settings = SettingsDataStore(dataStore)

        settings.setRestSoundEnabled(false)

        assertFalse(settings.restSoundEnabled.first())
        assertTrue("wibracja zostaje bez zmian", settings.restVibrationEnabled.first())
    }
}
