package com.gymtracker.data.media

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Własne miniatury załączników - **jedyne** pliki, jakie aplikacja trzyma dla
 * zdjęć i filmów z galerii.
 *
 * Leżą płasko w `filesDir/thumbs/<uuid>.jpg`, a nie w katalogach per ćwiczenie:
 * o przynależności decyduje wiersz w bazie, więc ćwiczenie bez załączników nie
 * zostawia po sobie pustego katalogu, a przenoszenie załącznika między
 * ćwiczeniami nie wymaga przenoszenia plików.
 *
 * Kasowanie idzie parami (wiersz + plik), ale pliki potrafią zostać same:
 * skasowanie ćwiczenia zabiera wiersze kaskadą, o plikach nie wiedząc.
 * Dlatego przy starcie aplikacji robimy jeden przebieg [sweep] - bez
 * `WorkManager`, bo do posprzątania własnego katalogu wystarczy jeden skan,
 * a nie zadanie w tle.
 */
@Singleton
class ThumbnailStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Ścieżka względna nowej miniatury (jeszcze nie istnieje na dysku). */
    fun allocate(): String = "$DIR/${UUID.randomUUID()}.jpg"

    fun resolve(relativePath: String): File = File(context.filesDir, relativePath)

    /** Plik miniatury, jeśli naprawdę istnieje (`null` = pokazujemy samą ikonę). */
    fun maybeFile(relativePath: String?): File? =
        relativePath?.let(::resolve)?.takeIf { file -> file.isFile }

    fun write(relativePath: String, bitmap: Bitmap): Boolean = runCatching {
        val file = resolve(relativePath)
        file.parentFile?.mkdirs()
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, stream)
        }
        true
    }.getOrDefault(false)

    fun delete(relativePath: String?) {
        if (relativePath == null) return
        runCatching { resolve(relativePath).delete() }
    }

    /**
     * Kasuje miniatury, których nie ma na liście [keep] (ścieżki względne).
     * Zwraca liczbę usuniętych plików.
     *
     * Świeżych plików nie ruszamy. Tuż po zapisie miniatury nie odróżnimy
     * sieroty od pliku, którego wiersz właśnie powstaje, więc sprzątanie
     * z założenia nie zabiera niczego „świeżego”. Wiek bierzemy z czasu
     * modyfikacji pliku, czyli z tego samego zegara systemowego, co teraz.
     */
    fun sweep(keep: Set<String>): Int {
        val oldest = System.currentTimeMillis() - MIN_ORPHAN_AGE_MS
        val files = File(context.filesDir, DIR).listFiles() ?: return 0
        return files.count { file ->
            val relativePath = "$DIR/${file.name}"
            file.isFile &&
                file.lastModified() < oldest &&
                relativePath !in keep &&
                runCatching { file.delete() }.getOrDefault(false)
        }
    }

    private companion object {
        const val DIR = "thumbs"

        /** Miniatura ma być mała - 80 to jeszcze dobra jakość przy tym rozmiarze. */
        const val THUMBNAIL_QUALITY = 80

        /** Ile musi mieć plik bez wiersza, żeby sprzątanie mogło go zabrać. */
        const val MIN_ORPHAN_AGE_MS = 24L * 60 * 60 * 1000
    }
}
