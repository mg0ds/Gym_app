package com.gymtracker

import android.app.Application
import com.gymtracker.domain.repository.MediaRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class GymTrackerApplication : Application() {

    @Inject
    lateinit var mediaRepository: MediaRepository

    /**
     * Zakres na zadania, które muszą przeżyć każdy ekran, ale nie pilnują żadnej
     * sesji - dziś tylko sprzątanie miniatur załączników.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Skasowanie ćwiczenia zabiera kaskadą wiersze załączników, ale nie
        // własne miniatury (M7) - dlatego przy starcie robimy jeden przebieg po
        // katalogu `filesDir/thumbs`. Bez `WorkManager`: to nasz własny katalog,
        // więc wystarczy skan, a nie zadanie w tle. Błąd sprzątania nie może
        // przewrócić aplikacji, więc idzie w `runCatching`.
        applicationScope.launch {
            runCatching { mediaRepository.sweepOrphanThumbnails() }
        }
    }
}
