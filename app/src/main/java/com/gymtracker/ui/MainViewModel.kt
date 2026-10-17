package com.gymtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.prefs.SettingsDataStore
import com.gymtracker.data.seed.SeedImporter
import com.gymtracker.domain.model.ThemeMode
import com.gymtracker.domain.timer.TrackedWorkout
import com.gymtracker.timer.WorkoutTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Motyw dla całej aplikacji - czytany raz w [com.gymtracker.MainActivity]. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsDataStore,
    seedImporter: SeedImporter,
    private val workoutTimer: WorkoutTimer,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = ThemeMode.DEFAULT,
    )

    /** Trwający trening - na tej podstawie prosimy o zgodę na powiadomienia. */
    val trackedWorkout: StateFlow<TrackedWorkout?> = workoutTimer.tracked

    /**
     * Pokazuje notyfikację treningu od razu po zgodzie użytkownika.
     *
     * Bez tego notyfikacja pojawiłaby się dopiero przy najbliższej zmianie stanu
     * (pauza), a przez cały trening byłaby niewidoczna.
     */
    fun onNotificationPermissionGranted() = workoutTimer.refreshNotification()

    init {
        // Pierwsze uruchomienie (albo nowsza wersja danych startowych): wczytaj
        // katalog ćwiczeń i plany. Import jest idempotentny i nie nadpisuje
        // zmian użytkownika, więc można go wołać przy każdym starcie aplikacji.
        viewModelScope.launch {
            runCatching { seedImporter.importStartDataIfNeeded() }
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
