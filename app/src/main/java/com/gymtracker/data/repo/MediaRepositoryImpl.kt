package com.gymtracker.data.repo

import com.gymtracker.data.db.daos.MediaDao
import com.gymtracker.data.db.entities.ExerciseMediaEntity
import com.gymtracker.data.media.ThumbnailStore
import com.gymtracker.domain.media.MediaLinker
import com.gymtracker.domain.model.ExerciseMedia
import com.gymtracker.domain.model.MediaKind
import com.gymtracker.domain.repository.LinkMediaResult
import com.gymtracker.domain.repository.MediaRejection
import com.gymtracker.domain.repository.MediaRepository
import com.gymtracker.domain.time.TimeProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Załączniki ćwiczenia (M7).
 *
 * Podpinanie pliku z galerii to cztery kroki: **poproś o trwały dostęp**,
 * **sprawdź** (czy w ogóle umiemy przeczytać plik i czy to zdjęcie/film),
 * **zrób miniaturę**, **zapisz wskazanie**. Kolejność jest istotna: gdyby zapis padł, zostaje tylko plik
 * miniatury, który sprzątnie [sweepOrphanThumbnails] przy następnym starcie;
 * gdybyśmy najpierw zapisali wiersz, użytkownik zobaczyłby kafelek bez obrazka.
 */
@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val mediaDao: MediaDao,
    private val linker: MediaLinker,
    private val thumbnails: ThumbnailStore,
    private val timeProvider: TimeProvider,
) : MediaRepository {

    override fun observeMedia(exerciseId: Long): Flow<List<ExerciseMedia>> =
        mediaDao.observeMedia(exerciseId).map { rows -> rows.mapNotNull { row -> row.toDomainOrNull() } }

    override suspend fun link(exerciseId: Long, uri: String): LinkMediaResult {
        // Najpierw prosimy o trwały dostęp: jeśli system go da, załącznik
        // przeżyje restart urządzenia. Gdy nie da, zapisujemy mimo to - nasza
        // miniatura i tak trzyma kafelek, a pełny plik działa, póki dostęp żyje.
        linker.requestPersistentAccess(uri)

        val probed = linker.probe(uri) ?: return LinkMediaResult.Rejected(MediaRejection.UNREADABLE)
        val kind = probed.kind ?: return LinkMediaResult.Rejected(MediaRejection.UNSUPPORTED)
        if (!kind.isGalleryFile) return LinkMediaResult.Rejected(MediaRejection.UNSUPPORTED)

        val thumbnailPath = linker.makeThumbnail(uri, kind)
        val position = mediaDao.maxPosition(exerciseId) + 1

        val entity = ExerciseMediaEntity(
            exerciseId = exerciseId,
            kind = kind.dbValue,
            remoteUri = uri,
            thumbnailPath = thumbnailPath,
            durationMs = probed.durationMs,
            sizeBytes = probed.sizeBytes,
            position = position,
            createdAtEpochMs = timeProvider.nowEpochMs(),
        )
        val id = mediaDao.insert(entity)

        return LinkMediaResult.Linked(
            ExerciseMedia(
                id = id,
                exerciseId = exerciseId,
                kind = kind,
                remoteUri = uri,
                thumbnailPath = thumbnailPath,
                caption = null,
                durationMs = probed.durationMs,
                sizeBytes = probed.sizeBytes,
                position = position,
            ),
        )
    }

    override suspend fun delete(mediaId: Long) {
        val existing = mediaDao.findById(mediaId) ?: return
        // Najpierw wiersz: dopóki on jest, miniatura jest potrzebna. Gdyby
        // kasowanie pliku się nie udało, sprzątanie dokończy robotę.
        mediaDao.deleteById(mediaId)
        thumbnails.delete(existing.thumbnailPath)
    }

    override suspend fun setCaption(mediaId: Long, caption: String?) {
        mediaDao.updateCaption(mediaId, caption?.trim()?.takeIf { it.isNotEmpty() })
    }

    override fun thumbnailFile(media: ExerciseMedia): File? = thumbnails.maybeFile(media.thumbnailPath)

    override suspend fun sweepOrphanThumbnails() {
        thumbnails.sweep(mediaDao.thumbnailPaths().toSet())
    }
}
