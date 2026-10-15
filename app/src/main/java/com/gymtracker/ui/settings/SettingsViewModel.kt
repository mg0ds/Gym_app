package com.gymtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.prefs.SettingsDataStore
import com.gymtracker.data.seed.SeedImporter
import com.gymtracker.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Stan przycisku wczytywania danych startowych. */
sealed interface SeedImportState {

    data object Idle : SeedImportState

    data object Running : SeedImportState

    data class Done(val imported: Int, val skipped: Int) : SeedImportState

    data object Failed : SeedImportState
}

/** Stan przycisku wczytywania planów startowych. */
sealed interface ProgramImportState {

    data object Idle : ProgramImportState

    data object Running : ProgramImportState

    data class Done(val imported: Int, val skipped: Int, val days: Int) : ProgramImportState

    data object Failed : ProgramImportState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val seedImporter: SeedImporter,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = ThemeMode.DEFAULT,
    )

    private val _seedImportState = MutableStateFlow<SeedImportState>(SeedImportState.Idle)
    val seedImportState: StateFlow<SeedImportState> = _seedImportState.asStateFlow()

    private val _programImportState = MutableStateFlow<ProgramImportState>(ProgramImportState.Idle)
    val programImportState: StateFlow<ProgramImportState> = _programImportState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /**
     * Wczytanie katalogu startowego na żądanie - przydaje się po aktualizacji
     * aplikacji, gdy doszły nowe ćwiczenia. Istniejących wpisów nie nadpisuje.
     */
    fun importSeedData() {
        if (_seedImportState.value == SeedImportState.Running) return

        _seedImportState.value = SeedImportState.Running
        viewModelScope.launch {
            _seedImportState.value = runCatching { seedImporter.importExercises() }.fold(
                onSuccess = { result ->
                    SeedImportState.Done(imported = result.imported, skipped = result.skipped)
                },
                onFailure = { SeedImportState.Failed },
            )
        }
    }

    /**
     * Wczytanie planów startowych. Najpierw upewniamy się, że katalog ćwiczeń
     * jest w bazie - plan wskazuje ćwiczenia po slugu, więc bez katalogu nie ma
     * czego do niego dopiąć.
     */
    fun importProgramData() {
        if (_programImportState.value == ProgramImportState.Running) return

        _programImportState.value = ProgramImportState.Running
        viewModelScope.launch {
            _programImportState.value = runCatching {
                seedImporter.importExercisesIfNeeded()
                seedImporter.importPrograms()
            }.fold(
                onSuccess = { result ->
                    ProgramImportState.Done(
                        imported = result.plansImported,
                        skipped = result.plansSkipped,
                        days = result.daysImported,
                    )
                },
                onFailure = { ProgramImportState.Failed },
            )
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
