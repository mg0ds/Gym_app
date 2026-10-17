package com.gymtracker.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
 * treningowymi (te idą do Room) - motyw, a od M5 domyślna długość przerwy
 * między seriami.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> ThemeMode.fromStorage(preferences[KEY_THEME_MODE]) }

    /**
     * Domyślna przerwa między seriami (w sekundach).
     *
     * To **ostatni** fallback celu: plan i karta ćwiczenia mają pierwszeństwo.
     * Wartość trzymamy w sekundach, bo tak jest w bazie i w planach - minuty
     * i sekundy robimy dopiero na ekranie.
     */
    val defaultRestSeconds: Flow<Int> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            preferences[KEY_DEFAULT_REST_SECONDS] ?: DEFAULT_REST_SECONDS
        }

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

    companion object {
        /** 90 s to typowa przerwa na siłowni - sensowny start bez ustawiania. */
        const val DEFAULT_REST_SECONDS = 90

        const val MIN_REST_SECONDS = 5
        const val MAX_REST_SECONDS = 900

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DEFAULT_REST_SECONDS = intPreferencesKey("default_rest_seconds")
    }
}
