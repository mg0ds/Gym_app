package com.gymtracker.timer

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.prefs.SettingsDataStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Alarm końca przerwy trafia na kanał zgodny z przełącznikami z Ustawień.
 *
 * Sprawdzamy prawdziwy `NotificationManager`, a nie nasze pola: kanał jest
 * umową z systemem, więc liczy się to, co system o nim wie (`shouldVibrate`,
 * `sound`, ważność).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RestNotifierTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsDataStore
    private lateinit var context: Context
    private lateinit var notifier: RestNotifier

    private val manager: NotificationManager
        get() = requireNotNull(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        scope = CoroutineScope(UnconfinedTestDispatcher() + Job())
        val file = File(temporaryFolder.newFolder(), "settings.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        settings = SettingsDataStore(dataStore)
        context = ApplicationProvider.getApplicationContext()
        // Bez tej zgody `showRestFinished` nie pokazuje niczego (Android 13+),
        // a wtedy test sprawdzałby ciszę zamiast kanału.
        shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        notifier = RestNotifier(context, settings)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `domyslne ustawienia daja kanal z dzwiekiem i wibracja`() = runTest {
        notifier.showRestFinished("Wyciskanie sztangi")

        val channel = requireNotNull(manager.getNotificationChannel(LEGACY_CHANNEL_ID))
        assertTrue(channel.shouldVibrate())
        assertNotNull("domyślnie alarm ma dźwięk", channel.sound)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun `bez wibracji kanal ma dzwiek, ale nie wibruje`() = runTest {
        settings.setRestVibrationEnabled(false)

        notifier.showRestFinished("Wyciskanie sztangi")

        val channel = requireNotNull(manager.getNotificationChannel("rest_timer_sound"))
        assertNotNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertNull(
            "kanał bez wibracji nie dostaje wzoru",
            manager.getNotificationChannel("rest_timer_vibration"),
        )
    }

    @Test
    fun `bez dzwieku kanal wibruje, ale milczy`() = runTest {
        settings.setRestSoundEnabled(false)

        notifier.showRestFinished("Wyciskanie sztangi")

        val channel = requireNotNull(manager.getNotificationChannel("rest_timer_vibration"))
        assertNull("wyłączony dźwięk to brak dźwięku w kanale", channel.sound)
        assertTrue(channel.shouldVibrate())
    }

    @Test
    fun `oba wylaczone daja cichy kanal`() = runTest {
        settings.setRestSoundEnabled(false)
        settings.setRestVibrationEnabled(false)

        notifier.showRestFinished("Wyciskanie sztangi")

        val channel = requireNotNull(manager.getNotificationChannel("rest_timer_silent"))
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertEquals(
            "cisza nie może zdejmować komunikatu z ekranu",
            NotificationManager.IMPORTANCE_HIGH,
            channel.importance,
        )
    }

    @Test
    fun `alarm trafia na kanal wybrany ustawieniem`() = runTest {
        settings.setRestSoundEnabled(false)

        notifier.showRestFinished("Wyciskanie sztangi")

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals("rest_timer_vibration", posted.channelId)
        assertEquals("Wyciskanie sztangi", posted.extras.getCharSequence("android.text"))
    }

    @Test
    fun `istniejacego kanalu nie przestawiamy`() = runTest {
        settings.setRestVibrationEnabled(false)
        notifier.showRestFinished("Pierwszy alarm")
        val before = requireNotNull(manager.getNotificationChannel("rest_timer_sound"))
        assertNotNull("kanał powstał z dźwiękiem", before.sound)

        // Ustawienie zmienia się na ciche, ale kanału „dźwięk" nie da się
        // przestawić - następny alarm idzie więc już na inny kanał.
        settings.setRestSoundEnabled(false)
        notifier.showRestFinished("Drugi alarm")

        val after = requireNotNull(manager.getNotificationChannel("rest_timer_sound"))
        assertNotNull("stary kanał zachowuje dźwięk", after.sound)
        assertEquals(before.name, after.name)
        assertNotNull(manager.getNotificationChannel("rest_timer_silent"))
    }
}
