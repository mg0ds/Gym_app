package com.gymtracker.data.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Size
import com.gymtracker.domain.media.MediaLinker
import com.gymtracker.domain.media.ProbedMedia
import com.gymtracker.domain.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Warstwa systemowa załączników: czyta metadane pliku z galerii i robi **własną**
 * miniaturę.
 *
 * Świadomie nie kopiujemy wybranego pliku - jesteśmy tylko wskazaniem do niego,
 * więc [probe] zagląda do `ContentResolver` po rozmiar, typ i czas trwania,
 * a [makeThumbnail] tworzy mały plik w `filesDir` (patrz [ThumbnailStore]).
 *
 * Miniatury robimy API platformy, a nie tym samym Coilem, którym rysujemy
 * siatkę: na Androidzie 10+ `loadThumbnail` korzysta z gotowych miniatur
 * systemu (szybko i z poprawioną orientacją), a niżej dekodujemy sami
 * i obracamy po EXIF. Dzięki temu warstwa danych nie zależy od biblioteki
 * obrazków, a siatka i podgląd pokazują to samo.
 */
@Singleton
class AndroidMediaLinker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val thumbnails: ThumbnailStore,
) : MediaLinker {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun requestPersistentAccess(uri: String): Boolean =
        withContext(Dispatchers.IO) {
            val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext false
            runCatching {
                resolver.takePersistableUriPermission(parsed, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
                // Photo Picker nie musi dawać trwałego dostępu i wtedy system
                // rzuca `SecurityException` - dla nas to nie błąd, tylko „tyle
                // dostaliśmy”.
            }.getOrDefault(false)
        }

    override suspend fun probe(uri: String): ProbedMedia? = withContext(Dispatchers.IO) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
        if (!isReadable(parsed)) return@withContext null

        val (size, displayName) = metadata(parsed)
        val kind = MediaKind.ofMimeType(mimeType(parsed), displayName)

        ProbedMedia(
            kind = kind,
            sizeBytes = size,
            durationMs = if (kind == MediaKind.VIDEO) durationMs(parsed) else null,
            displayName = displayName,
        )
    }

    override suspend fun makeThumbnail(uri: String, kind: MediaKind): String? =
        withContext(Dispatchers.IO) {
            val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
            val bitmap = when (kind) {
                MediaKind.VIDEO -> videoThumbnail(parsed)
                else -> imageThumbnail(parsed)
            } ?: return@withContext null

            val path = thumbnails.allocate()
            return@withContext if (thumbnails.write(path, bitmap)) path else null
        }

    // --- metadane ---------------------------------------------------------

    /** Nieczytelny plik poznajemy po tym, że nie umiemy otworzyć strumienia. */
    private fun isReadable(uri: Uri): Boolean =
        runCatching { resolver.openInputStream(uri)?.use { } != null }.getOrDefault(false)

    private fun mimeType(uri: Uri): String? = runCatching { resolver.getType(uri) }.getOrNull()

    /** Rozmiar i nazwa pliku z `ContentResolver`; brak wiersza = same `null`. */
    private fun metadata(uri: Uri): Pair<Long?, String?> {
        val projection = arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME)
        val row = runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    cursor.longOrNull(OpenableColumns.SIZE) to
                        cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)
                }
            }
        }.getOrNull()
        return row ?: (null to null)
    }

    private fun durationMs(uri: Uri): Long? = withRetriever(uri) { retriever ->
        retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
    }

    // --- miniatury --------------------------------------------------------

    private fun imageThumbnail(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val system = runCatching {
                resolver.loadThumbnail(
                    uri,
                    Size(MediaLinker.THUMBNAIL_MAX_PX, MediaLinker.THUMBNAIL_MAX_PX),
                    null,
                )
            }.getOrNull()
            // Systemowa miniatura bywa większa niż nasz bok, a bywa, że wcale
            // jej nie ma (np. zupełnie świeży plik) - wtedy dekodujemy sami.
            system?.let { bitmap -> return bitmap.fitted(MediaLinker.THUMBNAIL_MAX_PX) }
        }
        return decodeScaled(uri, MediaLinker.THUMBNAIL_MAX_PX)
    }

    private fun videoThumbnail(uri: Uri): Bitmap? = withRetriever(uri) { retriever ->
        val rotation = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
        val frame = representativeFrame(retriever) ?: return@withRetriever null
        // Film nagrany telefonem trzyma obrót w metadanych, więc sama klatka jest
        // „bokiem” - obracamy ją tak, jak zobaczy ją odtwarzacz.
        val upright = if (rotation != 0) frame.rotated(rotation.toFloat()) else frame
        upright.fitted(MediaLinker.THUMBNAIL_MAX_PX)
    }

    /**
     * Klatka reprezentatywna filmu. Na Androidzie 8.1+ prosimy od razu o klatkę
     * w rozmiarze miniatury (długi film nie zajmuje wtedy pamięci na pełną
     * klatkę), a niżej skalujemy to, co dostaliśmy. `-1 µs` znaczy „dowolna
     * reprezentatywna klatka”, więc miniatura nie zależy od tego, czy film
     * zaczyna się od czerni.
     */
    private fun representativeFrame(retriever: MediaMetadataRetriever): Bitmap? {
        val maxPx = MediaLinker.THUMBNAIL_MAX_PX
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val width = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.intMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (width != null && height != null && width > 0 && height > 0) {
                val scale = (maxPx.toFloat() / max(width, height)).coerceAtMost(1f)
                return runCatching {
                    retriever.getScaledFrameAtTime(
                        ANY_FRAME_US,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                }.getOrNull()
            }
        }
        return runCatching {
            retriever.getFrameAtTime(ANY_FRAME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }.getOrNull()
    }

    private fun decodeScaled(uri: Uri, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
        }
        val decoded = runCatching {
            resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
        }.getOrNull() ?: return null

        val upright = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            withExifRotation(decoded, uri)
        } else {
            decoded
        }
        return upright.fitted(maxPx)
    }

    /**
     * Orientacja zdjęcia z EXIF - tylko Android 9 i starsze, bo od 10 robi to
     * `loadThumbnail`. Platformowe `ExifInterface` jest przestarzałe na rzecz
     * `androidx.exifinterface`, ale starsze systemy nie mają nic innego,
     * a osobna biblioteka dla samego obrotu miniatury to koszt bez zysku.
     */
    @Suppress("DEPRECATION")
    private fun withExifRotation(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                android.media.ExifInterface(stream).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: return bitmap

        val degrees = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        return bitmap.rotated(degrees)
    }

    private fun <T> withRetriever(uri: Uri, block: (MediaMetadataRetriever) -> T?): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            block(retriever)
        } catch (error: RuntimeException) {
            // Nieczytelny plik (np. film, do którego wygasł dostęp) to nie awaria
            // aplikacji - po prostu nie mamy metadanych ani miniatury.
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        /** „Dowolna reprezentatywna klatka” - patrz [representativeFrame]. */
        const val ANY_FRAME_US = -1L

        fun MediaMetadataRetriever.intMetadata(key: Int): Int? =
            extractMetadata(key)?.toIntOrNull()
    }
}

/** Największy dzielnik potęgi dwójki, przy którym obraz mieści się w [maxPx]. */
internal fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxPx) {
        sample *= 2
    }
    return sample
}

/** Skaluje bitmapę w dół, jeśli którykolwiek bok jest większy niż [maxPx]. */
private fun Bitmap.fitted(maxPx: Int): Bitmap {
    val longest = max(width, height)
    if (longest <= maxPx) return this
    return scaled(maxPx.toFloat() / longest)
}

private fun Bitmap.scaled(scale: Float): Bitmap =
    Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true,
    )

private fun Bitmap.rotated(degrees: Float): Bitmap =
    Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(degrees) }, true)

private fun android.database.Cursor.longOrNull(columnName: String): Long? =
    getColumnIndex(columnName).takeIf { index -> index >= 0 && !isNull(index) }?.let { index -> getLong(index) }

private fun android.database.Cursor.stringOrNull(columnName: String): String? =
    getColumnIndex(columnName).takeIf { index -> index >= 0 && !isNull(index) }?.let { index -> getString(index) }
