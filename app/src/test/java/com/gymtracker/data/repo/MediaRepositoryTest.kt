package com.gymtracker.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.entities.ExerciseEntity
import com.gymtracker.data.media.ThumbnailStore
import com.gymtracker.domain.media.MediaLinker
import com.gymtracker.domain.media.ProbedMedia
import com.gymtracker.domain.model.MediaKind
import com.gymtracker.domain.repository.LinkMediaResult
import com.gymtracker.domain.repository.MediaRejection
import com.gymtracker.domain.time.TimeProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Załączniki ćwiczenia (M7): wskazanie zamiast kopii.
 *
 * Sprawdzamy kontrakt, na którym opiera się UI: co podpinamy, czego nie
 * podpinamy, co się dzieje z odmową i co zostaje na dysku po odpięciu.
 * Warstwę systemową (ContentResolver, MediaMetadataRetriever) zastępujemy
 * podstawionym linkerem - testujemy reguły, a nie Androida.
 */
@RunWith(RobolectricTestRunner::class)
class MediaRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var database: AppDatabase
    private lateinit var thumbnails: ThumbnailStore
    private lateinit var linker: FakeMediaLinker
    private lateinit var repository: MediaRepositoryImpl

    private var now = 1_000L
    private var exerciseId = 0L

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        thumbnails = ThumbnailStore(context)
        linker = FakeMediaLinker(thumbnails)
        repository = MediaRepositoryImpl(
            mediaDao = database.mediaDao(),
            linker = linker,
            thumbnails = thumbnails,
            timeProvider = TimeProvider { now },
        )
        exerciseId = database.exerciseDao().insert(
            ExerciseEntity(
                name = "Wyciskanie sztangą leżąc",
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "thumbs").deleteRecursively()
    }

    @Test
    fun `podpina zdjecie z galerii razem z wlasna miniatura`() = runTest {
        linker.probed = ProbedMedia(MediaKind.IMAGE, sizeBytes = 2_400_000L, durationMs = null, displayName = "foto.jpg")
        linker.thumbnail = "thumbs/zdjecie.jpg"

        val result = repository.link(exerciseId, "content://media/picker/1")

        val linked = requireNotNull(result as? LinkMediaResult.Linked).media
        assertEquals(MediaKind.IMAGE, linked.kind)
        assertEquals("content://media/picker/1", linked.remoteUri)
        assertEquals("thumbs/zdjecie.jpg", linked.thumbnailPath)
        assertEquals(2_400_000L, linked.sizeBytes)
        assertNull("zdjęcie nie ma czasu trwania", linked.durationMs)

        // Prosimy o trwały dostęp, zanim cokolwiek zapiszemy.
        assertEquals(listOf("content://media/picker/1"), linker.persistentAccessRequests)
        assertEquals(listOf("content://media/picker/1"), linker.thumbnailRequests)
        assertTrue("miniatura leży na dysku", thumbnails.resolve("thumbs/zdjecie.jpg").isFile)
    }

    @Test
    fun `film zapisuje czas trwania a kolejne zalaczniki ida na koniec`() = runTest {
        linker.probed = ProbedMedia(MediaKind.VIDEO, sizeBytes = null, durationMs = 83_000L, displayName = "film.mp4")
        linker.thumbnail = "thumbs/film.jpg"

        val first = requireNotNull(repository.link(exerciseId, "content://film") as? LinkMediaResult.Linked).media
        now = 2_000L
        val second = requireNotNull(repository.link(exerciseId, "content://zdjecie") as? LinkMediaResult.Linked).media

        assertEquals(83_000L, first.durationMs)
        assertEquals(0, first.position)
        assertEquals(1, second.position)

        val media = repository.observeMedia(exerciseId).first()
        assertEquals(listOf("content://film", "content://zdjecie"), media.map { it.remoteUri })
    }

    @Test
    fun `nieczytelny plik nie trafia do bazy`() = runTest {
        linker.probed = null

        val result = repository.link(exerciseId, "content://zniklo")

        assertEquals(MediaRejection.UNREADABLE, (result as? LinkMediaResult.Rejected)?.reason)
        assertTrue(repository.observeMedia(exerciseId).first().isEmpty())
        assertTrue("nie robimy miniatury, której nie ma z czego zrobić", linker.thumbnailRequests.isEmpty())
    }

    @Test
    fun `plik ktory nie jest zdjeciem ani filmem jest odrzucany`() = runTest {
        linker.probed = ProbedMedia(kind = null, sizeBytes = 1_000L, durationMs = null, displayName = "umowa.pdf")

        val result = repository.link(exerciseId, "content://umowa")

        assertEquals(MediaRejection.UNSUPPORTED, (result as? LinkMediaResult.Rejected)?.reason)
        assertTrue(repository.observeMedia(exerciseId).first().isEmpty())
    }

    @Test
    fun `odpiecie kasuje wiersz i nasza miniature ale nie rusza galerii`() = runTest {
        linker.probed = ProbedMedia(MediaKind.IMAGE, null, null, "foto.jpg")
        linker.thumbnail = "thumbs/do-usuniecia.jpg"
        val mediaId = requireNotNull(repository.link(exerciseId, "content://foto") as? LinkMediaResult.Linked).media.id
        val thumbnail = thumbnails.resolve("thumbs/do-usuniecia.jpg")
        assertTrue(thumbnail.isFile)

        repository.delete(mediaId)

        assertTrue(repository.observeMedia(exerciseId).first().isEmpty())
        assertFalse("nasza miniatura znika razem z wierszem", thumbnail.exists())
    }

    @Test
    fun `opis jest przycinany a pusty czysci kolumne`() = runTest {
        linker.probed = ProbedMedia(MediaKind.IMAGE, null, null, "foto.jpg")
        linker.thumbnail = "thumbs/opis.jpg"
        val mediaId = requireNotNull(repository.link(exerciseId, "content://foto") as? LinkMediaResult.Linked).media.id

        repository.setCaption(mediaId, "  Górna faza  ")
        assertEquals("Górna faza", repository.observeMedia(exerciseId).first().single().caption)

        repository.setCaption(mediaId, "   ")
        assertNull(repository.observeMedia(exerciseId).first().single().caption)
    }

    @Test
    fun `sprzatanie kasuje tylko stare miniatury bez wiersza w bazie`() = runTest {
        linker.probed = ProbedMedia(MediaKind.IMAGE, null, null, "foto.jpg")
        linker.thumbnail = "thumbs/w-bazie.jpg"
        repository.link(exerciseId, "content://foto")

        // Osierocenie powstaje tak, jak w życiu: wiersz znika kaskadą (skasowane
        // ćwiczenie), a plik zostaje. Postarzamy oba pliki, bo o zostaniu ma
        // decydować wiersz w bazie, a nie wiek - świeże sprzątanie i tak omija.
        val aged = System.currentTimeMillis() - 2 * DAY_MS
        val orphan = thumbnails.resolve("thumbs/bez-wiersza.jpg")
        orphan.parentFile?.mkdirs()
        orphan.writeBytes(byteArrayOf(1))
        orphan.setLastModified(aged)
        thumbnails.resolve("thumbs/w-bazie.jpg").setLastModified(aged)

        val fresh = thumbnails.resolve("thumbs/nowy.jpg")
        fresh.writeBytes(byteArrayOf(2))

        repository.sweepOrphanThumbnails()

        assertFalse("stara miniatura bez wiersza znika", orphan.exists())
        assertTrue("miniatura z wierszem zostaje", thumbnails.resolve("thumbs/w-bazie.jpg").isFile)
        assertTrue("świeżego pliku nie ruszamy", fresh.isFile)
    }

    @Test
    fun `plik miniatury znajdujemy tylko wtedy gdy naprawde jest`() = runTest {
        linker.probed = ProbedMedia(MediaKind.IMAGE, null, null, "foto.jpg")
        linker.thumbnail = "thumbs/jest.jpg"
        val media = requireNotNull(repository.link(exerciseId, "content://foto") as? LinkMediaResult.Linked).media

        assertNotNull(repository.thumbnailFile(media))
        assertNull(repository.thumbnailFile(media.copy(thumbnailPath = "thumbs/nie-ma.jpg")))
        assertNull(repository.thumbnailFile(media.copy(thumbnailPath = null)))
    }

    @Test
    fun `wiersz z nieznanym rodzajem nie trafia na liste`() = runTest {
        // Rodzaj z nowszej wersji bazy nie może przewrócić siatki w starszej apce.
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO exercise_media (exercise_id, kind, remote_uri, position, created_at) " +
                "VALUES ($exerciseId, 'hologram', 'content://x', 0, $now)",
        )

        assertTrue(repository.observeMedia(exerciseId).first().isEmpty())
    }

    /** Warstwa systemowa podstawiona: mówi, co „widzi”, i notuje wywołania. */
    private class FakeMediaLinker(private val thumbnails: ThumbnailStore) : MediaLinker {

        var probed: ProbedMedia? = ProbedMedia(MediaKind.IMAGE, 1_000L, null, "plik.jpg")

        /** Ścieżka miniatury, którą „tworzymy”; `null` = miniatury nie ma. */
        var thumbnail: String? = null

        val persistentAccessRequests = mutableListOf<String>()
        val thumbnailRequests = mutableListOf<String>()

        override suspend fun requestPersistentAccess(uri: String): Boolean {
            persistentAccessRequests += uri
            return true
        }

        override suspend fun probe(uri: String): ProbedMedia? = probed

        override suspend fun makeThumbnail(uri: String, kind: MediaKind): String? {
            thumbnailRequests += uri
            val path = thumbnail ?: return null
            thumbnails.resolve(path).let { file ->
                file.parentFile?.mkdirs()
                file.writeBytes(byteArrayOf(1, 2, 3))
            }
            return path
        }
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
