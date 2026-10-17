package com.gymtracker.domain.repository

import com.gymtracker.domain.model.ExerciseMedia
import java.io.File
import kotlinx.coroutines.flow.Flow

/** Dlaczego nie podpięliśmy wybranego pliku. */
enum class MediaRejection {
    /** Nie umiemy przeczytać pliku (brak dostępu, plik zniknął, obcy format). */
    UNREADABLE,

    /** Plik jest czytelny, ale to nie zdjęcie i nie film. */
    UNSUPPORTED,
}

sealed interface LinkMediaResult {
    data class Linked(val media: ExerciseMedia) : LinkMediaResult

    data class Rejected(val reason: MediaRejection) : LinkMediaResult
}

/**
 * Załączniki ćwiczenia (M7).
 *
 * Repozytorium **nie kopiuje** plików z galerii - zapisuje tylko wskazanie
 * (`content://`) i robi własną miniaturę. Dzięki temu:
 *
 * * użytkownik zostaje właścicielem swoich zdjęć, a apka nie zajmuje miejsca
 *   podwójnie,
 * * usunięcie zdjęcia w galerii usuwa je też z apki (i to jest zachowanie
 *   oczekiwane, nie błąd),
 * * nie ma czego sprzątać poza własnymi miniaturami.
 */
interface MediaRepository {

    fun observeMedia(exerciseId: Long): Flow<List<ExerciseMedia>>

    /**
     * Podpina plik z galerii pod ćwiczenie: czyta metadane, robi miniaturę
     * i zapisuje wiersz. Nie rzuca - mówi, że się nie udało.
     */
    suspend fun link(exerciseId: Long, uri: String): LinkMediaResult

    /** Odpina załącznik: kasuje wiersz i własną miniaturę (galerii nie rusza). */
    suspend fun delete(mediaId: Long)

    /** Opis załącznika; pusty tekst czyści opis. */
    suspend fun setCaption(mediaId: Long, caption: String?)

    /** Bezwzględna ścieżka własnej miniatury (`null`, gdy jej nie ma). */
    fun thumbnailFile(media: ExerciseMedia): File?

    /**
     * Kasuje własne miniatury, których nie ma już w bazie.
     *
     * Osierocenia biorą się z kaskady: skasowanie ćwiczenia zabiera wiersze
     * załączników, ale nie pliki w `filesDir`. Wołane przy starcie aplikacji
     * (nie w `WorkManager` - do posprzątania własnego katalogu wystarczy jeden
     * przebieg, a nie zadanie w tle).
     */
    suspend fun sweepOrphanThumbnails()
}
