package com.gymtracker.data.backup

import android.content.Context
import android.net.Uri
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.domain.repository.MediaRepository
import com.gymtracker.domain.time.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Kopia zapasowa jako plik ZIP: spina bazę ([BackupExporter], [BackupImporter])
 * z plikami i dostępem do pamięci urządzenia.
 *
 * Plik wybiera użytkownik przez systemowy wybór dokumentu (SAF), więc aplikacja
 * nie potrzebuje żadnego uprawnienia do pamięci - a ZIP jest jeden, bo obok
 * danych muszą pojechać własne miniatury. Katalog `media/` w archiwum używa
 * tych samych ścieżek, co baza (`exercise_media.thumbnail_path`, względne
 * wobec `filesDir`), dzięki czemu wczytanie kopii nie musi niczego przepisywać
 * w wierszach.
 *
 * **Wersję schematu czytamy z bazy, nie z osobnej stałej** (`user_version`,
 * czyli to samo, co w adnotacji `@Database`): druga stała do trzymania w zgodzie
 * to dokładnie ten rodzaj rozjazdu, który psuje importy po cichej zmianie.
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val mediaRepository: MediaRepository,
    private val timeProvider: TimeProvider,
) {

    suspend fun export(uri: Uri, appVersion: String): BackupExportSummary =
        withContext(Dispatchers.IO) {
            val tables = exporter.collect()
            val payload = exporter.payload(
                tables = tables,
                schemaVersion = schemaVersion(),
                appVersion = appVersion,
                nowEpochMs = timeProvider.nowEpochMs(),
            )
            val json = BackupFormat.json.encodeToString(BackupPayload.serializer(), payload)

            var thumbnails = 0
            writeArchive(uri) { zip ->
                zip.putNextEntry(ZipEntry(BackupFormat.JSON_ENTRY))
                zip.write(json.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Jedna miniatura może wisieć przy kilku załącznikach (np. dwa
                // wpisy tego samego pliku z galerii), dlatego `distinct`.
                tables.exerciseMedia
                    .mapNotNull { media -> media.thumbnailPath }
                    .distinct()
                    .forEach { path ->
                        val file = resolveInFilesDir(path) ?: return@forEach
                        if (!file.isFile) return@forEach
                        zip.putNextEntry(ZipEntry("${BackupFormat.MEDIA_DIR}/$path"))
                        file.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                        thumbnails++
                    }
            }

            BackupExportSummary(rowCount = tables.rowCount, thumbnails = thumbnails)
        }

    /**
     * Podgląd kopii: co jest w pliku, zanim cokolwiek wczytamy. Sprawdza też
     * format i `schema_version`, więc użytkownik nie potwierdza wczytania pliku,
     * którego i tak nie da się wczytać.
     */
    suspend fun readHeader(uri: Uri): BackupHeader = withContext(Dispatchers.IO) {
        val payload = readPayload(uri)
        validate(payload)
        BackupHeader(
            schemaVersion = payload.schemaVersion,
            appVersion = payload.appVersion,
            exportedAtEpochMs = payload.exportedAtEpochMs,
            rowCount = payload.tables.rowCount,
        )
    }

    suspend fun import(uri: Uri, mode: BackupImportMode): BackupImportSummary =
        withContext(Dispatchers.IO) {
            val payload = readPayload(uri)
            validate(payload)

            // Najpierw baza (jedna transakcja), potem pliki. Gdyby import bazy
            // padł, nie zapisujemy miniaturek - zostałyby śmieci bez wierszy
            // (i tak posprzątałby je startowy `sweepOrphanThumbnails`, ale
            // lepiej nie tworzyć ich wcale).
            val summary = importer.import(payload.tables, mode)

            var written = 0
            readArchive(uri) { zip, entry ->
                if (!entry.isDirectory && entry.name.startsWith("${BackupFormat.MEDIA_DIR}/")) {
                    val path = entry.name.removePrefix("${BackupFormat.MEDIA_DIR}/")
                    val file = resolveInFilesDir(path)
                    if (file != null) {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { output -> zip.copyTo(output) }
                        written++
                    }
                }
            }

            // Po podmianie danych stare miniatury nie mają już wierszy.
            mediaRepository.sweepOrphanThumbnails()

            summary
        }

    /** Wersja schematu bazy (`user_version`), czyli wartość z `@Database`. */
    private fun schemaVersion(): Int = db.openHelper.readableDatabase.version

    private inline fun writeArchive(uri: Uri, body: (ZipOutputStream) -> Unit) {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Brak dostępu do pliku kopii")
        stream.use { output ->
            ZipOutputStream(BufferedOutputStream(output)).use { zip -> body(zip) }
        }
    }

    private inline fun readArchive(uri: Uri, onEntry: (ZipInputStream, ZipEntry) -> Unit) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Brak dostępu do pliku kopii")
        stream.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    onEntry(zip, entry)
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
    }

    /**
     * Czyta z archiwum sam `gymtracker.json`. Wpisy przed nim pomijamy, więc
     * kolejność w pliku nie ma znaczenia - nasz eksport i tak pisze go pierwszy.
     */
    private fun readPayload(uri: Uri): BackupPayload {
        var payload: BackupPayload? = null
        readArchive(uri) { zip, entry ->
            if (payload == null && !entry.isDirectory && entry.name == BackupFormat.JSON_ENTRY) {
                payload = try {
                    BackupFormat.json.decodeFromString(
                        BackupPayload.serializer(),
                        zip.readBytes().decodeToString(),
                    )
                } catch (error: SerializationException) {
                    throw BackupImportException(BackupImportError.CORRUPT)
                } catch (error: IllegalArgumentException) {
                    throw BackupImportException(BackupImportError.CORRUPT)
                }
            }
        }
        return payload ?: throw BackupImportException(BackupImportError.NOT_A_BACKUP)
    }

    private fun validate(payload: BackupPayload) {
        if (payload.format != BackupFormat.FORMAT) {
            throw BackupImportException(BackupImportError.NOT_A_BACKUP)
        }
        if (payload.schemaVersion > schemaVersion()) {
            throw BackupImportException(BackupImportError.TOO_NEW)
        }
    }

    /**
     * Ścieżka z kopii rozwiązana wobec `filesDir` - i tylko tam.
     *
     * Kopia mogłaby nieść ścieżkę z `..`, więc sprawdzamy kanoniczną postać:
     * pliki zapisujemy wyłącznie w katalogu aplikacji.
     */
    private fun resolveInFilesDir(relativePath: String): File? {
        if (relativePath.isBlank()) return null
        val root = context.filesDir
        val file = File(root, relativePath)
        val rootPath = root.canonicalPath + File.separator
        return if (file.canonicalPath.startsWith(rootPath)) file else null
    }
}
