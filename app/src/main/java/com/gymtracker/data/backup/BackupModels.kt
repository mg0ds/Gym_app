package com.gymtracker.data.backup

import com.gymtracker.data.db.entities.AppMetaEntity
import com.gymtracker.data.db.entities.BodyEntryEntity
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.db.entities.ExerciseMediaEntity
import com.gymtracker.data.db.entities.MetricTypeEntity
import com.gymtracker.data.db.entities.PlanDayEntity
import com.gymtracker.data.db.entities.PlanEntity
import com.gymtracker.data.db.entities.PlanExerciseEntity
import com.gymtracker.data.db.entities.RecurrenceEntity
import com.gymtracker.data.db.entities.RestIntervalEntity
import com.gymtracker.data.db.entities.ScheduledSessionEntity
import com.gymtracker.data.db.entities.SeedImportEntity
import com.gymtracker.data.db.entities.SessionEntity
import com.gymtracker.data.db.entities.SessionExerciseEntity
import com.gymtracker.data.db.entities.SetLogEntity
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Kopia zapasowa (M9): jeden plik ZIP z `gymtracker.json` w środku i katalogiem
 * `media/` z własnymi miniaturami.
 *
 * **Dlaczego JSON, a nie kopia pliku bazy.** Kopia pliku `.db` byłaby prostsza,
 * ale oddawałaby bazę razem z jej przypadkami: trzeba by zamykać połączenie,
 * pilnować plików `-wal`/`-shm` i przywracać binarny format, którego nie da się
 * ani podejrzeć, ani sprawdzić wersji przed wczytaniem. JSON jest czytelny
 * (da się zajrzeć, co się przywraca), jawnie niesie `schema_version` - więc
 * plik z nowszej wersji aplikacji jest odrzucany **przed** dotknięciem bazy -
 * i nie zależy od wersji SQLite.
 *
 * **Dlaczego nazwy pól jak w tabelach.** Kopia ma być zrozumiała dla kogoś, kto
 * zna bazę albo czyta `docs/SCHEMA.sql`, dlatego `Json` używa `snake_case`
 * (`exercise_media`, `set_logs`, `created_at_epoch_ms`), a nie nazw właściwości
 * z Kotlina. Nie owijamy wierszy w osobne modele DTO: encje Room są płaskimi
 * data classami, więc kopia jest ich wiernym obrazem, a różnice między wersjami
 * pilnuje `schema_version`.
 *
 * **Czego nie ma w kopii.** Tabeli `exercises_fts` - to indeks wyszukiwania
 * odbudowywany triggerami przy każdym zapisie do `exercises`, więc wczytanie
 * katalogu odtworzy go samo. Miniatury są w ZIP-ie pod ścieżkami zapisanymi
 * w `exercise_media.thumbnail_path` (względnymi wobec `filesDir`), a pliki
 * z galerii - nie: aplikacja ich nie kopiuje, tylko wskazuje URI (§4.2 pkt 43).
 */
@Serializable
data class BackupPayload(
    val format: String = BackupFormat.FORMAT,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAtEpochMs: Long,
    val tables: BackupTables,
)

/** Wszystkie tabele kopii - jeden wiersz na tabelę, w kolejności czytania. */
@Serializable
data class BackupTables(
    val exercises: List<ExerciseEntity> = emptyList(),
    val exerciseMedia: List<ExerciseMediaEntity> = emptyList(),
    val plans: List<PlanEntity> = emptyList(),
    val planDays: List<PlanDayEntity> = emptyList(),
    val planDayExercises: List<PlanExerciseEntity> = emptyList(),
    val recurrences: List<RecurrenceEntity> = emptyList(),
    val scheduledSessions: List<ScheduledSessionEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val sessionExercises: List<SessionExerciseEntity> = emptyList(),
    val setLogs: List<SetLogEntity> = emptyList(),
    val restIntervals: List<RestIntervalEntity> = emptyList(),
    val metricTypes: List<MetricTypeEntity> = emptyList(),
    val bodyEntries: List<BodyEntryEntity> = emptyList(),
    val appMeta: List<AppMetaEntity> = emptyList(),
    val seedImports: List<SeedImportEntity> = emptyList(),
) {
    val rowCount: Int
        get() = exercises.size + exerciseMedia.size + plans.size + planDays.size +
            planDayExercises.size + recurrences.size + scheduledSessions.size +
            sessions.size + sessionExercises.size + setLogs.size + restIntervals.size +
            metricTypes.size + bodyEntries.size + appMeta.size + seedImports.size
}

/** Jak wczytać kopię: zastąpić wszystko albo dopisać do tego, co już jest. */
enum class BackupImportMode {
    /** Kasuje obecne dane i wstawia kopię wiernie - razem z identyfikatorami. */
    REPLACE,

    /** Dopisuje wiersze obok obecnych; identyfikatory są nowe, powiązania zostają. */
    APPEND,
}

/** Co pokazać w potwierdzeniu, **zanim** cokolwiek dotkniemy w bazie. */
data class BackupHeader(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAtEpochMs: Long,
    val rowCount: Int,
)

/** Wynik wczytania kopii - ile wierszy weszło, ile użyło istniejących, ile wypadło. */
data class BackupImportSummary(
    val mode: BackupImportMode,
    val inserted: Int,
    val reused: Int,
    val skipped: Int,
)

/** Wynik eksportu - ile wierszy i plików poszło do ZIP-a. */
data class BackupExportSummary(
    val rowCount: Int,
    val thumbnails: Int,
)

/** Powód odmowy wczytania kopii. UI tłumaczy go na zdanie w swoim języku. */
enum class BackupImportError {
    /** Plik nie jest kopią GymTracker (brak `gymtracker.json` albo zły `format`). */
    NOT_A_BACKUP,

    /** `schema_version` nowszy niż baza tej wersji aplikacji. */
    TOO_NEW,

    /** Kopia jest uszkodzona (JSON się nie parsuje, brakuje pól). */
    CORRUPT,

    /** Trwa trening - najpierw trzeba go zakończyć (inaczej stan w pamięci
     *  wskazywałby na wiersze, których po wczytaniu kopii już nie ma). */
    WORKOUT_IN_PROGRESS,
}

class BackupImportException(val error: BackupImportError) : Exception(error.name)

/**
 * Format pliku kopii w jednym miejscu: nazwa wpisu w ZIP-ie, katalog na media
 * i konfiguracja JSON-a.
 */
object BackupFormat {

    const val FORMAT = "gymtracker-backup"

    /** Nazwa wpisu z danymi w ZIP-ie. */
    const val JSON_ENTRY = "gymtracker.json"

    /** Katalog na miniatury wewnątrz ZIP-a (bez ukośnika na końcu). */
    const val MEDIA_DIR = "media"

    /**
     * `encodeDefaults = true` jest tu istotne: bez tego pola równe domyślnym
     * (`is_warmup = false`, `paused_total_ms = 0`) zniknęłyby z pliku, a ich
     * wartość odtworzyłaby się z domyślnej encji - czyli z kodu, a nie z kopii.
     */
    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
}
