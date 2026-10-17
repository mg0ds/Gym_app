package com.gymtracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gymtracker.R
import com.gymtracker.domain.stats.RestStats
import com.gymtracker.domain.timer.formatElapsed

/**
 * „Średnia przerwa: 1:45 • ostatnio 2:05 (12 treningów)”.
 *
 * Wspólne dla ekranu treningu, karty ćwiczenia i statystyk: to ta sama liczba
 * z tych samych danych (`RestStats`), więc nie może być zapisana na trzy
 * sposoby. Liczba treningów idzie przez `plurals`, bo polski ma trzy formy
 * mnogie - sklejanie tego w jednym napisie dałoby „1 treningów”.
 */
@Composable
fun restHistorySummary(stats: RestStats): String {
    val average = formatElapsed((stats.averageSeconds ?: 0) * 1_000L)
    val last = formatElapsed((stats.lastSeconds ?: 0) * 1_000L)
    val workouts = pluralStringResource(
        R.plurals.session_rest_sessions,
        stats.sessions,
        stats.sessions,
    )
    return stringResource(R.string.session_rest_history_summary, average, last) + " ($workouts)"
}
