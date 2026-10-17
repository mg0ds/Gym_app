package com.gymtracker.domain.model

/**
 * Rodzaj załącznika ćwiczenia.
 *
 * Dwa pierwsze rodzaje to pliki **z galerii** (mamy do nich URI i własną
 * miniaturę), dwa kolejne to linki otwierane poza aplikacją. `youtube` i `link`
 * są na razie zapisywane tylko przez import danych (film ćwiczenia mieszka
 * w `Exercise.youtubeUrl`), ale słownik jest pełny, żeby schemat bazy nie
 * musiał się zmieniać, gdy dojdzie „dodaj link”.
 */
enum class MediaKind(val dbValue: String) {
    IMAGE("image"),
    VIDEO("video"),
    YOUTUBE("youtube"),
    LINK("link"),
    ;

    /** Czy to plik z galerii (ma URI, miniaturę i da się go pokazać w apce). */
    val isGalleryFile: Boolean get() = this == IMAGE || this == VIDEO

    companion object {

        fun fromDb(value: String?): MediaKind? =
            entries.firstOrNull { kind -> kind.dbValue == value }

        /** Rodzaj pliku po typie MIME; `null`, gdy to nie zdjęcie ani film. */
        fun ofMimeType(mimeType: String?, fileName: String? = null): MediaKind? = when {
            mimeType == null -> ofFileName(fileName)
            mimeType.startsWith("image/") -> IMAGE
            mimeType.startsWith("video/") -> VIDEO
            else -> null
        }

        /**
         * Rodzaj pliku po nazwie - gdy dostawca nie podał typu MIME. Świadomie
         * sprawdzamy też nazwę, bo część dostawców (i część chmur) zwraca
         * `null` jako typ, a wtedy jedyne, co o pliku wiemy, to jego rozszerzenie.
         */
        fun ofFileName(fileName: String?): MediaKind? {
            val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
            return when (extension) {
                "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp" -> IMAGE
                "mp4", "mov", "m4v", "avi", "mkv", "webm", "3gp" -> VIDEO
                else -> null
            }
        }
    }
}

/**
 * Załącznik ćwiczenia: zdjęcie albo film z galerii.
 *
 * [remoteUri] to **wskazanie** do pliku w galerii, a nie kopia: aplikacja nie
 * zarządza cudzymi plikami. Gdy użytkownik usunie zdjęcie w galerii, załącznik
 * po prostu przestaje się otwierać - i o tym mówi wprost, zamiast pokazywać
 * pustkę. [thumbnailPath] (ścieżka względna w `filesDir`) jest naszą własną,
 * małą miniaturą, więc siatka ćwiczenia wygląda tak samo nawet wtedy, gdy
 * dostęp do oryginału wygasł.
 */
data class ExerciseMedia(
    val id: Long,
    val exerciseId: Long,
    val kind: MediaKind,
    val remoteUri: String?,
    val thumbnailPath: String?,
    val caption: String?,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val position: Int,
) {
    /** Czy pokazujemy to w apce (zdjęcie/film), czy otwieramy poza nią (link). */
    val isGalleryFile: Boolean get() = kind.isGalleryFile
}
