package com.gymtracker.data.seed

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Czytanie pliku katalogu wybranego systemowym wyborem dokumentu (SAF, M9).
 *
 * Aplikacja nie prosi o żadne uprawnienie do pamięci: dostaje URI, który
 * użytkownik właśnie wskazał, i czyta tylko ten jeden plik.
 *
 * Nazwę i treść czytamy **osobno**, bo nazwa jest potrzebna nawet wtedy, gdy
 * odczyt się nie uda - dziennik importów zapisuje też pliki odrzucone, a wpis
 * „nie udało się” bez nazwy pliku niczego nie wyjaśnia. Nazwa bierze się
 * z dostawcy dokumentu (`OpenableColumns`), a gdy go o nią nie zapytać -
 * z ostatniego odcinka ścieżki URI.
 */
@Singleton
class CatalogFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun name(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) {
                        val value = cursor.getString(index)
                        if (!value.isNullOrBlank()) return@withContext value
                    }
                }
        }
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
    }

    /** Treść pliku jako tekst (UTF-8). Rzuca [IOException], gdy plik zniknął. */
    suspend fun text(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Brak dostępu do pliku katalogu")
        stream.use { input -> input.readBytes().decodeToString() }
    }

    private companion object {
        const val DEFAULT_NAME = "katalog.json"
    }
}
