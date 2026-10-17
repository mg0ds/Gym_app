package com.gymtracker.domain.media

import com.gymtracker.domain.model.MediaKind

/**
 * Metadane wybranego pliku, odczytane **z systemu** (bez kopiowania).
 *
 * Sprawdzamy je jeszcze przed zapisem wiersza, żeby od razu wiedzieć, co
 * podpinamy, i umieć odmówić, gdy plik jest nieczytelny albo nie jest ani
 * zdjęciem, ani filmem.
 */
data class ProbedMedia(
    /** `null`, gdy to nie zdjęcie i nie film (np. PDF wybrany z innego źródła). */
    val kind: MediaKind?,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val displayName: String?,
)

/**
 * Warstwa systemowa załączników: czyta metadane pliku i robi własną miniaturę.
 *
 * Interfejs jest w domenie, a implementacja (ContentResolver, MediaMetadataRetriever)
 * w warstwie danych - dzięki temu reguły („co podpinamy, co odrzucamy”) testuje
 * się bez Androida.
 */
interface MediaLinker {

    /**
     * Prosi system o **trwały** dostęp do pliku.
     *
     * Domyślny grant z galerii żyje do restartu urządzenia albo do zatrzymania
     * aplikacji, a my zapisujemy wskazanie na dłużej. Gdy URI takiego dostępu
     * nie oferuje (Photo Picker nie musi), zwracamy `false` i nic się nie dzieje:
     * kafelek dalej pokazuje naszą miniaturę, a pełny plik - dopóki dostęp
     * działa.
     */
    suspend fun requestPersistentAccess(uri: String): Boolean

    /** Metadane pliku; `null`, gdy plik jest nieczytelny (brak dostępu, już go nie ma). */
    suspend fun probe(uri: String): ProbedMedia?

    /**
     * Własna miniatura pliku - ścieżka **względna** w `filesDir`.
     * `null`, gdy nie udało się jej zrobić (wtedy kafelek pokaże tylko ikonę).
     */
    suspend fun makeThumbnail(uri: String, kind: MediaKind): String?

    companion object {
        /**
         * Bok miniatury: dość, żeby rozpoznać ćwiczenie w siatce, i dość mało,
         * żeby własny plik nie urósł do rozmiarów zdjęcia.
         */
        const val THUMBNAIL_MAX_PX = 200
    }
}
