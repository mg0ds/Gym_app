package com.gymtracker.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.data.backup.BackupHeader
import com.gymtracker.data.backup.BackupImportError
import com.gymtracker.data.backup.BackupImportException
import com.gymtracker.data.backup.BackupImportMode
import com.gymtracker.data.backup.BackupService
import com.gymtracker.data.prefs.SettingsDataStore
import com.gymtracker.data.seed.CatalogFileReader
import com.gymtracker.data.seed.CatalogImportResult
import com.gymtracker.data.seed.SeedImportErrors
import com.gymtracker.data.seed.SeedImportJournal
import com.gymtracker.data.seed.SeedImportLog
import com.gymtracker.data.seed.SeedImporter
import com.gymtracker.domain.model.ThemeMode
import com.gymtracker.domain.time.TimeProvider
import com.gymtracker.domain.time.today
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

/** Stan operacji na kopii zapasowej (eksport, podgląd, wczytanie). */
sealed interface BackupState {

    data object Idle : BackupState

    data object Working : BackupState

    data class Exported(val rows: Int, val thumbnails: Int) : BackupState

    data class Restored(
        val mode: BackupImportMode,
        val inserted: Int,
        val reused: Int,
        val skipped: Int,
    ) : BackupState

    /** [error] `null` = nie wiemy, co się stało (np. brak dostępu do pliku). */
    data class Failed(val error: BackupImportError?) : BackupState
}

/** Kopia wskazana przez użytkownika, zanim ją wczytamy. */
data class PendingBackup(val uri: Uri, val header: BackupHeader)

/** Stan wczytywania własnego katalogu ćwiczeń z pliku. */
sealed interface CatalogState {

    data object Idle : CatalogState

    data object Working : CatalogState

    data class Done(val fileName: String, val imported: Int, val skipped: Int) : CatalogState

    data class Rejected(val fileName: String, val errorCode: String) : CatalogState

    data object Failed : CatalogState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val seedImporter: SeedImporter,
    private val backupService: BackupService,
    private val catalogFileReader: CatalogFileReader,
    private val journal: SeedImportJournal,
    private val timeProvider: TimeProvider,
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

    /** Domyślna przerwa między seriami - ostatni fallback celu przerwy (M5). */
    val defaultRestSeconds: StateFlow<Int> = settings.defaultRestSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = SettingsDataStore.DEFAULT_REST_SECONDS,
    )

    /** Czy koniec przerwy ma się odezwać dźwiękiem (M9). */
    val restSoundEnabled: StateFlow<Boolean> = settings.restSoundEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = SettingsDataStore.DEFAULT_REST_ALERT,
    )

    /** Czy koniec przerwy ma się odezwać wibracją (M9). */
    val restVibrationEnabled: StateFlow<Boolean> = settings.restVibrationEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = SettingsDataStore.DEFAULT_REST_ALERT,
    )

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _pendingBackup = MutableStateFlow<PendingBackup?>(null)
    val pendingBackup: StateFlow<PendingBackup?> = _pendingBackup.asStateFlow()

    private val _catalogState = MutableStateFlow<CatalogState>(CatalogState.Idle)
    val catalogState: StateFlow<CatalogState> = _catalogState.asStateFlow()

    /** Ostatnie importy katalogów - `assets/...` i pliki użytkownika. */
    val recentImports: StateFlow<List<SeedImportLog>> = journal.observeRecent().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = emptyList(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDefaultRestSeconds(seconds: Int) {
        viewModelScope.launch { settings.setDefaultRestSeconds(seconds) }
    }

    fun setRestSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setRestSoundEnabled(enabled) }
    }

    fun setRestVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setRestVibrationEnabled(enabled) }
    }

    /**
     * Nazwa pliku proponowana przy eksporcie: data, nie godzina - kopie robi
     * się raz dziennie, a plik z godziną w nazwie tylko utrudnia wybór tego
     * właściwego na liście. Liczy się w chwili kliknięcia, więc nie zastyga
     * o północy.
     */
    fun backupFileName(): String {
        val date = timeProvider.today()
        val stamp = "%d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
        return "gymtracker-$stamp.zip"
    }

    /** Eksport kopii: zapis wybranego pliku ZIP. */
    fun exportBackup(uri: Uri, appVersion: String) {
        if (_backupState.value == BackupState.Working) return

        _backupState.value = BackupState.Working
        viewModelScope.launch {
            _backupState.value = runCatching { backupService.export(uri, appVersion) }.fold(
                onSuccess = { summary ->
                    BackupState.Exported(rows = summary.rowCount, thumbnails = summary.thumbnails)
                },
                onFailure = { BackupState.Failed(error = null) },
            )
        }
    }

    /**
     * Podgląd kopii: czytamy nagłówek i pytamy, czy wczytać. Potwierdzenie jest
     * tu konieczne, bo import może usunąć obecne dane - a użytkownik nie widzi
     * zawartości pliku, dopóki mu jej nie pokażemy.
     */
    fun inspectBackup(uri: Uri) {
        if (_backupState.value == BackupState.Working) return

        _backupState.value = BackupState.Working
        viewModelScope.launch {
            runCatching { backupService.readHeader(uri) }.fold(
                onSuccess = { header ->
                    _pendingBackup.value = PendingBackup(uri = uri, header = header)
                    _backupState.value = BackupState.Idle
                },
                onFailure = { error -> _backupState.value = BackupState.Failed(error.asImportError()) },
            )
        }
    }

    fun dismissBackup() {
        _pendingBackup.value = null
    }

    /** Wczytanie potwierdzonej kopii w wybranym trybie. */
    fun confirmBackup(mode: BackupImportMode) {
        val pending = _pendingBackup.value ?: return
        if (_backupState.value == BackupState.Working) return

        _pendingBackup.value = null
        _backupState.value = BackupState.Working
        viewModelScope.launch {
            _backupState.value = runCatching { backupService.import(pending.uri, mode) }.fold(
                onSuccess = { summary ->
                    BackupState.Restored(
                        mode = summary.mode,
                        inserted = summary.inserted,
                        reused = summary.reused,
                        skipped = summary.skipped,
                    )
                },
                onFailure = { error -> BackupState.Failed(error.asImportError()) },
            )
        }
    }

    /** Wczytanie własnego katalogu ćwiczeń z pliku JSON (ten sam format co seed). */
    fun importCatalog(uri: Uri) {
        if (_catalogState.value == CatalogState.Working) return

        _catalogState.value = CatalogState.Working
        viewModelScope.launch {
            val fileName = catalogFileReader.name(uri)
            val text = runCatching { catalogFileReader.text(uri) }.getOrNull()
            if (text == null) {
                journal.recordRejected(fileName, checksum = null, errorCode = SeedImportErrors.IO)
                _catalogState.value = CatalogState.Rejected(fileName, SeedImportErrors.IO)
                return@launch
            }

            _catalogState.value = when (val result = runCatching { seedImporter.importCatalogFile(fileName, text) }.getOrNull()) {
                is CatalogImportResult.Done -> CatalogState.Done(
                    fileName = result.fileName,
                    imported = result.imported,
                    skipped = result.skipped,
                )
                is CatalogImportResult.Rejected -> CatalogState.Rejected(result.fileName, result.errorCode)
                null -> CatalogState.Failed
            }
        }
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

    /**
     * Dziennik to jedyne miejsce, w którym błąd importu ma jeszcze sens po
     * zamknięciu ekranu, więc wyjątki spoza `BackupImportException` (brak
     * dostępu do pliku, odebrane uprawnienie) tracą szczegóły, a zostaje samo
     * „nie udało się”.
     */
    private fun Throwable.asImportError(): BackupImportError? =
        (this as? BackupImportException)?.error

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
