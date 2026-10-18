package com.gymtracker.data.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.domain.model.ExerciseMedia
import com.gymtracker.domain.repository.LinkMediaResult
import com.gymtracker.domain.repository.MediaRepository
import com.gymtracker.domain.repository.MediaRejection
import com.gymtracker.domain.time.TimeProvider
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Kopia zapasowa jako plik: ZIP, miniatury i odmowy.
 *
 * Testy jadą przez prawdziwy `ContentResolver` i prawdziwe pliki, bo tu
 * najłatwiej o błąd, którego nie widać w samym imporcie: ścieżka miniatury
 * wyprowadzona poza katalog aplikacji, brak wpisu z danymi, kopia z nowszej
 * wersji. Każda z tych rzeczy kończy się albo cudzym plikiem na dysku, albo
 * połowicznie wczytanymi danymi - więc każdą pilnujemy osobno.
 */
@RunWith(RobolectricTestRunner::class)
class BackupServiceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var filesRoot: File
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var media: RecordingMediaRepository
    private lateinit var service: BackupService

    @Before
    fun setUp() {
        filesRoot = temporaryFolder.newFolder("files")
        context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun getFilesDir(): File = filesRoot
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        media = RecordingMediaRepository()
        service = BackupService(
            context = context,
            db = database,
            exporter = BackupExporter(database.backupDao()),
            importer = BackupImporter(database, database.backupDao()),
            mediaRepository = media,
            timeProvider = TimeProvider { EXPORT_TIME },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `eksport zapisuje dane i miniatury a import je przywraca`() = runTest {
        fillBackupData(database)
        val thumbnail = writeThumbnail()
        val before = readAllTables(database)

        val archive = archiveFile("kopia.zip")
        val export = service.export(Uri.fromFile(archive), APP_VERSION)

        assertEquals(before.rowCount, export.rowCount)
        assertEquals(1, export.thumbnails)
        assertEquals(
            listOf(BackupFormat.JSON_ENTRY, "media/${SeededIds.THUMBNAIL_PATH}"),
            ZipFile(archive).use { zip -> zip.entries().toList().map { entry -> entry.name } },
        )

        // Czyszczenie do zera - dokładnie to, co użytkownik ma po instalacji.
        BackupImporter(database, database.backupDao()).import(BackupTables(), BackupImportMode.REPLACE)
        thumbnail.delete()
        assertEquals(0, readAllTables(database).rowCount)

        val summary = service.import(Uri.fromFile(archive), BackupImportMode.REPLACE)

        assertEquals(before.rowCount, summary.inserted)
        assertEquals(0, summary.skipped)
        assertEquals(before, readAllTables(database))
        assertArrayEquals(THUMBNAIL_BYTES, thumbnail.readBytes())
        assertEquals(1, media.sweeps)
    }

    @Test
    fun `podglad pokazuje naglowek bez dotykania bazy`() = runTest {
        fillBackupData(database)
        val archive = archiveFile("kopia.zip")
        service.export(Uri.fromFile(archive), APP_VERSION)

        val header = service.readHeader(Uri.fromFile(archive))

        // Wersja schematu musi być tą, którą naprawdę ma baza - nie osobną stałą.
        assertEquals(database.openHelper.writableDatabase.version, header.schemaVersion)
        assertEquals(APP_VERSION, header.appVersion)
        assertEquals(EXPORT_TIME, header.exportedAtEpochMs)
        assertEquals(readAllTables(database).rowCount, header.rowCount)
    }

    @Test
    fun `plik bez danych kopii jest odrzucany`() = runTest {
        val archive = archiveFile("obcy.zip")
        writeZip(archive) { zip ->
            zip.putNextEntry(ZipEntry("readme.txt"))
            zip.write("to nie jest kopia".toByteArray())
            zip.closeEntry()
        }

        assertEquals(BackupImportError.NOT_A_BACKUP, importError(archive))
    }

    @Test
    fun `kopia z nowszej wersji aplikacji jest odrzucana`() = runTest {
        val archive = archiveFile("nowsza.zip")
        writePayloadZip(
            file = archive,
            payload = BackupPayload(
                schemaVersion = database.openHelper.writableDatabase.version + 1,
                appVersion = "9.9.9",
                exportedAtEpochMs = EXPORT_TIME,
                tables = BackupTables(),
            ),
        )

        assertEquals(BackupImportError.TOO_NEW, importError(archive))
        // Odmowa przyszła przed dotknięciem bazy - nic nie zostało wczytane.
        assertEquals(0, readAllTables(database).rowCount)
    }

    @Test
    fun `uszkodzone dane sa odrzucane`() = runTest {
        val archive = archiveFile("uszkodzona.zip")
        writeZip(archive) { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.JSON_ENTRY))
            zip.write("""{"format":"gymtracker-backup","schema_version":""".toByteArray())
            zip.closeEntry()
        }

        assertEquals(BackupImportError.CORRUPT, importError(archive))
    }

    @Test
    fun `miniatura spoza katalogu aplikacji nie jest zapisywana`() = runTest {
        val archive = archiveFile("zlosliwa.zip")
        writePayloadZip(
            file = archive,
            payload = BackupPayload(
                schemaVersion = database.openHelper.writableDatabase.version,
                appVersion = APP_VERSION,
                exportedAtEpochMs = EXPORT_TIME,
                tables = BackupTables(),
            ),
            extraEntries = mapOf("media/../evil.txt" to "nie tu".toByteArray()),
        )

        service.import(Uri.fromFile(archive), BackupImportMode.REPLACE)

        assertFalse(File(filesRoot.parentFile, "evil.txt").exists())
        assertFalse(File(filesRoot.parentFile, "..").canonicalFile.resolve("evil.txt").exists())
    }

    private fun writeThumbnail(): File {
        val file = File(filesRoot, SeededIds.THUMBNAIL_PATH)
        file.parentFile?.mkdirs()
        file.writeBytes(THUMBNAIL_BYTES)
        return file
    }

    private suspend fun importError(archive: File): BackupImportError? =
        runCatching { service.import(Uri.fromFile(archive), BackupImportMode.REPLACE) }
            .exceptionOrNull()
            .let { error -> (error as? BackupImportException)?.error }
            .also { assertTrue("oczekiwano odmowy", it != null) }

    private fun archiveFile(name: String): File = File(temporaryFolder.root, name)

    private fun writeZip(file: File, body: (ZipOutputStream) -> Unit) {
        ZipOutputStream(BufferedOutputStream(file.outputStream())).use { zip -> body(zip) }
    }

    private fun writePayloadZip(
        file: File,
        payload: BackupPayload,
        extraEntries: Map<String, ByteArray> = emptyMap(),
    ) {
        val json = BackupFormat.json.encodeToString(BackupPayload.serializer(), payload)
        writeZip(file) { zip ->
            zip.putNextEntry(ZipEntry(BackupFormat.JSON_ENTRY))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            extraEntries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /**
     * Atrapa repozytorium multimediów: kopię interesuje tylko sprzątanie
     * sierot po podmianie danych. Reszta metod nie jest w tym teście używana,
     * więc zwraca puste wartości zamiast udawać działanie.
     */
    private class RecordingMediaRepository : MediaRepository {

        var sweeps = 0

        override fun observeMedia(exerciseId: Long): Flow<List<ExerciseMedia>> = flowOf(emptyList())

        override suspend fun link(exerciseId: Long, uri: String): LinkMediaResult =
            LinkMediaResult.Rejected(MediaRejection.UNREADABLE)

        override suspend fun delete(mediaId: Long) = Unit

        override suspend fun setCaption(mediaId: Long, caption: String?) = Unit

        override fun thumbnailFile(media: ExerciseMedia): File? = null

        override suspend fun sweepOrphanThumbnails() {
            sweeps++
        }
    }

    private companion object {
        const val APP_VERSION = "0.1.0"
        const val EXPORT_TIME = 1_760_000_000_000L
        val THUMBNAIL_BYTES = byteArrayOf(0x11, 0x22, 0x33, 0x44)
    }
}
