package com.gymtracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.stats.SessionSummary
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.domain.timer.formatElapsed
import com.gymtracker.ui.calendar.isoText

/**
 * Historia treningów: co było zrobione i kiedy.
 *
 * Wiersz pokazuje to, co da się przeczytać bez wchodzenia w trening: nazwę dnia,
 * datę z czasem, liczbę serii, objętość i średnią przerwę. Szczegóły (serie,
 * przerwy, notatka) są pod stuknięciem - ten sam ekran treningu co w M4/M5,
 * tylko w trybie do czytania.
 */
@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryContent(
        uiState = uiState,
        onRangeSelected = viewModel::onRangeSelected,
        onExerciseSelected = viewModel::onExerciseSelected,
        onNameSelected = viewModel::onNameSelected,
        onOpenSession = onOpenSession,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(
    uiState: HistoryUiState,
    onRangeSelected: (StatsRange) -> Unit,
    onExerciseSelected: (Long?) -> Unit,
    onNameSelected: (String?) -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RangeChipsRow(range = uiState.range, onRangeSelected = onRangeSelected)

            // Filtry pokazujemy tylko wtedy, gdy mają co zawężać - jeden
            // trening w historii nie potrzebuje trzech pasków chipów.
            if (uiState.exercises.size > 1) {
                ExerciseChipsRow(
                    exercises = uiState.exercises,
                    selectedId = uiState.exerciseId,
                    onExerciseSelected = onExerciseSelected,
                )
            }
            if (uiState.names.size > 1) {
                NameChipsRow(
                    names = uiState.names,
                    selectedName = uiState.name,
                    onNameSelected = onNameSelected,
                )
            }

            when {
                uiState.sessions.isEmpty() -> EmptyState(hasFilter = uiState.hasFilter)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.sessions, key = { summary -> summary.sessionId }) { summary ->
                        SessionRow(summary = summary, onClick = onOpenSession)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    summary: SessionSummary,
    onClick: (Long) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(summary.sessionId) },
        headlineContent = { Text(summary.name) },
        supportingContent = {
            Column {
                Text(
                    text = summary.durationMs?.let { duration ->
                        stringResource(
                            R.string.history_row_head,
                            summary.date.isoText(),
                            formatElapsed(duration),
                        )
                    } ?: summary.date.isoText(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = details(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Trening „na zapas” wchodzi do zakresu (inaczej zniknąłby
                // z historii), więc mówimy wprost, że jego data to przyszłość.
                if (summary.isFuture) {
                    Text(
                        text = stringResource(R.string.history_row_future),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
    )
}

/** „24 serie • 8 450 kg • śr. przerwa 1:30” - części, których nie ma, wypadają. */
@Composable
private fun details(summary: SessionSummary): String {
    val parts = mutableListOf<String>()
    if (summary.workingSets > 0) {
        parts += pluralStringResource(
            R.plurals.history_sets,
            summary.workingSets,
            summary.workingSets,
        )
    }
    if (summary.volumeKg > 0.0) {
        parts += stringResource(R.string.history_row_volume, formatKilograms(summary.volumeKg))
    }
    summary.restAverageSeconds?.let { seconds ->
        parts += stringResource(R.string.history_row_rest, formatElapsed(seconds * 1_000L))
    }
    return parts.joinToString(separator = " • ")
}

@Composable
private fun EmptyState(hasFilter: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (hasFilter) R.string.history_empty_filtered else R.string.history_empty,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
