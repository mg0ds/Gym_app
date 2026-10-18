package com.gymtracker.data.seed

import com.gymtracker.data.db.daos.SeedImportDao
import com.gymtracker.data.db.entities.SeedImportEntity
import com.gymtracker.domain.time.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Podsumowanie importu katalogu - treść `seed_imports.stats_json`.
 *
 * Trzymamy tu **kod** błędu, nie zdanie: użytkownik może mieć interfejs po
 * polsku albo angielsku, a dziennik jest jeden dla obu. Tłumaczenie powstaje
 * dopiero w UI, na podstawie [errorCode].
 */
@Serializable
data class SeedImportStats(
    val status: String,
    val imported: Int = 0,
    val skipped: Int = 0,
    val total: Int = 0,
    val error: String? = null,
) {

    val isRejected: Boolean get() = status == STATUS_REJECTED

    companion object {
        const val STATUS_DONE = "done"
        const val STATUS_REJECTED = "rejected"

        fun done(imported: Int, skipped: Int, total: Int): SeedImportStats =
            SeedImportStats(
                status = STATUS_DONE,
                imported = imported,
                skipped = skipped,
                total = total,
            )

        fun rejected(errorCode: String): SeedImportStats =
            SeedImportStats(status = STATUS_REJECTED, error = errorCode)
    }
}

/** Powody odrzucenia pliku katalogu - UI tłumaczy je na swój język. */
object SeedImportErrors {
    /** Plik nie jest poprawnym JSON-em. */
    const val BAD_JSON = "bad_json"

    /** Brak `schema_version` - nie wiadomo, z czym mamy do czynienia. */
    const val MISSING_SCHEMA_VERSION = "missing_schema_version"

    /** Plik deklaruje nowszy format, niż zna ta wersja aplikacji. */
    const val TOO_NEW_SCHEMA = "too_new_schema"

    /** Poprawny plik, ale bez ani jednego ćwiczenia. */
    const val EMPTY_CATALOG = "empty_catalog"

    /** Nie udało się odczytać pliku z pamięci urządzenia. */
    const val IO = "io_error"
}

/**
 * Wpis dziennika importów w postaci, jakiej potrzebuje UI.
 *
 * [seedId] to nazwa pliku wybranego przez użytkownika albo ścieżka danych
 * z APK (`assets/...`), a [stats] `null`, gdy zapis w dzienniku jest
 * nieczytelny - wtedy pokazujemy wpis bez szczegółów, zamiast go ukrywać.
 */
data class SeedImportLog(
    val id: Long,
    val seedId: String,
    val version: String?,
    val importedAtEpochMs: Long,
    val stats: SeedImportStats?,
) {
    val isRejected: Boolean get() = stats?.isRejected == true
}

/**
 * Dziennik importów katalogów (tabela `seed_imports`, M9).
 *
 * Dlaczego osobna klasa, a nie DAO wstrzyknięte tam, gdzie trzeba: zapis do
 * dziennika ma jedną regułę (kiedy, co i w jakiej formie), a odczyt drugą
 * (tolerancyjny `stats_json`). Obie trzymamy razem, żeby nie rozjechały się
 * między `SeedImporter` a ekranem Ustawień.
 */
@Singleton
class SeedImportJournal @Inject constructor(
    private val dao: SeedImportDao,
    private val timeProvider: TimeProvider,
) {

    /** Ostatnie importy, od najnowszego - do pokazania w Ustawieniach. */
    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<SeedImportLog>> =
        dao.observeRecent(limit).map { rows -> rows.map { row -> row.toLog() } }

    suspend fun recordDone(
        seedId: String,
        version: String?,
        checksum: String?,
        stats: SeedImportStats,
    ) {
        dao.insert(
            SeedImportEntity(
                seedId = seedId,
                version = version,
                checksum = checksum,
                importedAtEpochMs = timeProvider.nowEpochMs(),
                statsJson = json.encodeToString(SeedImportStats.serializer(), stats),
            ),
        )
    }

    suspend fun recordRejected(seedId: String, checksum: String?, errorCode: String) {
        recordDone(
            seedId = seedId,
            version = null,
            checksum = checksum,
            stats = SeedImportStats.rejected(errorCode),
        )
    }

    /** Ostatni wpis o tej samej treści pliku - `null`, gdy plik jest nowy. */
    suspend fun lastWithChecksum(checksum: String): SeedImportLog? =
        dao.findLastWithChecksum(checksum)?.toLog()

    private fun SeedImportEntity.toLog(): SeedImportLog = SeedImportLog(
        id = id,
        seedId = seedId,
        version = version,
        importedAtEpochMs = importedAtEpochMs,
        stats = statsJson?.let { text ->
            runCatching { json.decodeFromString(SeedImportStats.serializer(), text) }
                .getOrElse { error ->
                    if (error is SerializationException || error is IllegalArgumentException) {
                        null
                    } else {
                        throw error
                    }
                }
        },
    )

    private companion object {
        const val DEFAULT_LIMIT = 5

        val json = Json { ignoreUnknownKeys = true }
    }
}
