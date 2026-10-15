package com.gymtracker.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skąd import czyta dane startowe. Interfejs istnieje po to, żeby testy mogły
 * podać własną treść JSON zamiast plików z APK.
 */
interface SeedSource {
    suspend fun readText(assetPath: String): String
}

@Singleton
class AssetSeedSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SeedSource {

    override suspend fun readText(assetPath: String): String = withContext(Dispatchers.IO) {
        context.assets.open(assetPath).bufferedReader().use { reader -> reader.readText() }
    }
}
