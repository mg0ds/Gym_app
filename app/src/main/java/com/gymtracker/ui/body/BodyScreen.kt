package com.gymtracker.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.body.BodyChange
import com.gymtracker.domain.body.BodyProgress
import com.gymtracker.domain.model.BodyEntry
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.model.MetricType
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.ui.calendar.isoText
import com.gymtracker.ui.components.InfoRow
import com.gymtracker.ui.components.SectionCard
import com.gymtracker.ui.history.ChartLegend
import com.gymtracker.ui.history.FilterChipsRow
import com.gymtracker.ui.history.RangeChipsRow
import java.time.LocalDate

/**
 * Karta postaci: jeden wskaźnik naraz, z wykresem, podsumowaniem i listą pomiarów.
 *
 * Dwie decyzje warte zapisania:
 * 1. **Kafelki zamiast dziesięciu mini-wykresów.** Plan zakładał siatkę
 *    mini-wykresów per wskaźnik; przy dziesięciu wbudowanych wskaźnikach byłaby
 *    to ściana liczb, na której nie widać żadnej zmiany. Przełącznik u góry
 *    daje tę samą informację, a wykres zostaje czytelny.
 * 2. **Zakres i legenda są współdzielone z historią** (`RangeChipsRow`,
 *    `ChartLegend`): to ten sam wybór 1M/3M/6M/1R/wszystko, więc jedno miejsce
 *    decyduje o nazwach i wyglądzie.
 */
@Composable
fun BodyScreen(
    onOpenMetrics: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BodyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BodyContent(
        uiState = uiState,
        onOpenMetrics = onOpenMetrics,
        onTypeSelected = viewModel::onTypeSelected,
        onRangeSelected = viewModel::onRangeSelected,
        onSaveEntry = viewModel::saveEntry,
        onDeleteEntry = viewModel::deleteEntry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyContent(
    uiState: BodyUiState,
    onOpenMetrics: () -> Unit,
    onTypeSelected: (Long) -> Unit,
    onRangeSelected: (StatsRange) -> Unit,
    onSaveEntry: (Long?, LocalDate, Double, String?) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editedEntry by remember { mutableStateOf<BodyEntry?>(null) }
    var entryDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.metric_title)) },
                actions = {
                    IconButton(onClick = onOpenMetrics) {
                        Icon(
                            imageVector = Icons.Filled.List,
                            contentDescription = stringResource(R.string.metric_manage_open),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isLoading) return@Column

            if (uiState.types.isEmpty()) {
                EmptyState(onOpenMetrics = onOpenMetrics)
                return@Column
            }

            MetricChipsRow(
                types = uiState.types,
                selectedId = uiState.selected?.id,
                onTypeSelected = onTypeSelected,
            )

            val selected = uiState.selected ?: return@Column

            ChartCard(
                type = selected,
                progress = uiState.progress,
                range = uiState.range,
                onRangeSelected = onRangeSelected,
                onAddEntry = {
                    editedEntry = null
                    entryDialogOpen = true
                },
            )

            SummaryCard(type = selected, uiState = uiState)

            EntriesCard(
                entries = uiState.entriesInRange,
                unit = selected.unit,
                onEdit = { entry ->
                    editedEntry = entry
                    entryDialogOpen = true
                },
            )
        }
    }

    if (entryDialogOpen) {
        BodyEntryDialog(
            initial = editedEntry,
            unit = uiState.selected?.unit,
            today = uiState.today,
            onDismiss = { entryDialogOpen = false },
            onSave = { entryId, date, value, note ->
                onSaveEntry(entryId, date, value, note)
                entryDialogOpen = false
            },
            onDelete = { entry ->
                onDeleteEntry(entry.id)
                entryDialogOpen = false
            },
        )
    }
}

/** Przełącznik wskaźników - te same chipy co filtry w historii. */
@Composable
private fun MetricChipsRow(
    types: List<MetricType>,
    selectedId: Long?,
    onTypeSelected: (Long) -> Unit,
) {
    FilterChipsRow {
        types.forEach { type ->
            FilterChip(
                selected = type.id == selectedId,
                onClick = { onTypeSelected(type.id) },
                label = { Text(text = type.displayName()) },
            )
        }
    }
}

@Composable
private fun ChartCard(
    type: MetricType,
    progress: BodyProgress,
    range: StatsRange,
    onRangeSelected: (StatsRange) -> Unit,
    onAddEntry: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.metric_chart_title),
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        RangeChipsRow(range = range, onRangeSelected = onRangeSelected)

        if (progress.hasChart) {
            BodyProgressChart(points = progress.points, target = type.target)
            ChartLegend(
                entries = buildList {
                    add(
                        MaterialTheme.colorScheme.primary to
                            stringResource(R.string.metric_chart_legend_value),
                    )
                    if (type.target != null) {
                        add(
                            MaterialTheme.colorScheme.tertiary to
                                stringResource(R.string.metric_chart_legend_target),
                        )
                    }
                },
            )
        } else {
            Text(
                text = stringResource(R.string.metric_chart_too_few),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(onClick = onAddEntry, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.metric_add_entry))
        }
    }
}

@Composable
private fun SummaryCard(type: MetricType, uiState: BodyUiState) {
    val progress = uiState.progress

    SectionCard(
        title = stringResource(R.string.metric_summary_title),
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        val latest = progress.latest
        if (latest == null) {
            Text(
                text = stringResource(R.string.metric_summary_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        InfoRow(
            label = stringResource(R.string.metric_summary_latest),
            value = stringResource(
                R.string.metric_value_with_days_ago,
                metricValueWithDate(latest, type.unit),
                daysAgo(progress.daysSinceLatest ?: 0L),
            ),
        )
        progress.previous?.let { change ->
            ChangeRow(
                label = stringResource(R.string.metric_summary_previous),
                change = change,
                unit = type.unit,
                direction = type.direction,
            )
        }
        progress.last30Days?.let { change ->
            ChangeRow(
                label = stringResource(R.string.metric_summary_days_30),
                change = change,
                unit = type.unit,
                direction = type.direction,
            )
        }
        progress.last90Days?.let { change ->
            ChangeRow(
                label = stringResource(R.string.metric_summary_days_90),
                change = change,
                unit = type.unit,
                direction = type.direction,
            )
        }
        InfoRow(
            label = stringResource(R.string.metric_summary_target),
            value = metricTargetText(type, latest.value),
        )
        if (progress.streakDays > 0) {
            InfoRow(
                label = stringResource(R.string.metric_summary_streak),
                value = streakText(progress.streakDays),
            )
        }
        InfoRow(
            label = stringResource(R.string.metric_summary_entries),
            value = progress.entriesInRange.toString(),
        )
    }
}

/** Zmiana z kolorem: „na plus” zależy od tego, w którą stronę użytkownik chce iść. */
@Composable
private fun ChangeRow(
    label: String,
    change: BodyChange,
    unit: String?,
    direction: MetricDirection,
) {
    val color = when (direction.isImprovement(change.value)) {
        true -> MaterialTheme.colorScheme.primary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = metricChangeText(change, unit),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun EntriesCard(
    entries: List<BodyEntry>,
    unit: String?,
    onEdit: (BodyEntry) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.metric_entries_title),
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.metric_entries_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        entries.forEach { entry ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = metricValue(entry.value, unit),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = entry.date.isoText(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onEdit(entry) }) {
                        Text(
                            text = stringResource(R.string.action_edit),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                entry.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onOpenMetrics: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.metric_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenMetrics) {
            Text(text = stringResource(R.string.metric_metrics_title))
        }
    }
}
