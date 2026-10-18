package com.gymtracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gymtracker.domain.model.ThemeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Ustawienia użytkownika w DataStore. Trzymamy tu wszystko, co nie jest danymi
 * treningowymi (te idą do Room) - motyw, domyślną przerwę (M5) i alarm końca
 * przerwy (M9).
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = preferences()
        .map { preferences -> ThemeMode.fromStorage(preferences[KEY_THEME_MODE]) }

    /**
     * Domyślna przerwa między seriami (w sekundach).
     *
     * To **ostatni** fallback celu: plan i karta ćwiczenia mają pierwszeństwo.
     * Wartość trzymamy w sekundach, bo tak jest w bazie i w planach - minuty
     * i sekundy robimy dopiero na ekranie.
     */
    val defaultRestSeconds: Flow<Int> =
        preferenceFlow(KEY_DEFAULT_REST_SECONDS, DEFAULT_REST_SECONDS)

    /** Czy koniec przerwy ma się odezwać dźwiękiem. */
    val restSoundEnabled: Flow<Boolean> = preferenceFlow(KEY_REST_SOUND, DEFAULT_REST_ALERT)

    /** Czy koniec przerwy ma się odezwać wibracją. */
    val restVibrationEnabled: Flow<Boolean> =
        preferenceFlow(KEY_REST_VIBRATION, DEFAULT_REST_ALERT)

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.storageValue }
    }

    suspend fun setDefaultRestSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_REST_SECONDS] = seconds.coerceIn(
                MIN_REST_SECONDS,
                MAX_REST_SECONDS,
            )
        }
    }

    suspend fun setRestSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_REST_SOUND] = enabled }
    }

    suspend fun setRestVibrationEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_REST_VIBRATION] = enabled }
    }

    /**
     * Wspólny odczyt: brak wpisu to wartość domyślna, a uszkodzony plik
     * traktujemy jak pusty, żeby ekran ustawień nie przewracał się na starcie.
     */
    private fun <T> preferenceFlow(key: Preferences.Key<T>, default: T): Flow<T> =
        preferences().map { preferences -> preferences[key] ?: default }

    private fun preferences(): Flow<Preferences> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }

    companion object {
        /** 90 s to typowa przerwa na siłowni - sensowny start bez ustawiania. */
        const val DEFAULT_REST_SECONDS = 90

        const val MIN_REST_SECONDS = 5
        const val MAX_REST_SECONDS = 900

        /** Alarm przerwy domyślnie i słychać, i czuć - tak było od M5. */
        const val DEFAULT_REST_ALERT = true

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
        private val KEY_REST_SOUND = booleanPreferencesKey("rest_sound_enabled")
        private val KEY_REST_VIBRATION = booleanPreferencesKey("rest_vibration_enabled")
    }
}
