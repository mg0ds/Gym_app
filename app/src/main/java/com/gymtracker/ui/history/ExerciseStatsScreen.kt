package com.gymtracker.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.stats.ExerciseStats
import com.gymtracker.domain.stats.ExerciseStatsPoint
import com.gymtracker.domain.stats.StatsRange
import com.gymtracker.domain.timer.formatElapsed
import com.gymtracker.ui.calendar.isoText
import com.gymtracker.ui.components.InfoRow
import com.gymtracker.ui.components.SectionCard
import com.gymtracker.ui.components.restHistorySummary
import com.gymtracker.ui.components.toPlainStringOrNull

/**
 * Statystyki ćwiczenia: rekordy, wykresy postępu i tabela wykonań.
 *
 * Rekordy i średnie pokazują **całą historię**, a wykres i tabela - wybrany
 * zakres (decyzja z `ExerciseStatsCalculator`). Stąd wprost napisane „w
 * zakresie” przy podsumowaniu: dwie liczby obok siebie muszą mieć jasne źródło.
 */
@Composable
fun ExerciseStatsScreen(
    onBack: () -> Unit,
    viewModel: ExerciseStatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExerciseStatsContent(
        uiState = uiState,
        onBack = onBack,
        onRangeSelected = viewModel::onRangeSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseStatsContent(
    uiState: ExerciseStatsUiState,
    onBack: () -> Unit,
    onRangeSelected: (StatsRange) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.exerciseName ?: stringResource(R.string.exercise_stats_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
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
            RangeChipsRow(range = uiState.range, onRangeSelected = onRangeSelected)

            if (uiState.isLoading) return@Column

            RecordsCard(stats = uiState.stats)

            if (uiState.stats.hasData) {
                WeightCard(points = uiState.stats.points, hasWeightData = uiState.stats.hasWeightData)
                if (uiState.stats.hasWeightData) {
                    VolumeCard(points = uiState.stats.points)
                }
                PerformancesCard(points = uiState.stats.points)
            }
        }
    }
}

@Composable
private fun RecordsCard(stats: ExerciseStats) {
    SectionCard(
        title = stringResource(R.string.exercise_stats_records),
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        if (!stats.hasData) {
            Text(
                text = stringResource(R.string.exercise_stats_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        stats.records.heaviestSet?.let { record ->
            InfoRow(
                label = stringResource(R.string.exercise_stats_record_heaviest),
                value = recordText(record),
            )
        }
        stats.records.bestOneRepMax?.let { record ->
            InfoRow(
                label = stringResource(R.string.exercise_stats_record_one_rep_max),
                value = oneRepMaxText(record),
            )
            if (record.approximate) {
                Text(
                    text = stringResource(R.string.exercise_stats_one_rep_max_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        stats.records.bestVolume?.let { record ->
            InfoRow(
                label = stringResource(R.string.exercise_stats_record_volume),
                value = stringResource(
                    R.string.exercise_stats_volume_with_date,
                    formatKilograms(record.volumeKg),
                    record.date.isoText(),
                ),
            )
        }
        if (stats.rest.hasSamples) {
            InfoRow(
                label = stringResource(R.string.exercise_stats_record_rests),
                value = restHistorySummary(stats.rest),
            )
        }
        HorizontalDivider()
        InfoRow(
            label = stringResource(R.string.exercise_stats_range_sessions),
            value = stats.sessions.toString(),
        )
        InfoRow(
            label = stringResource(R.string.exercise_stats_range_sets),
            value = stats.sets.toString(),
        )
        InfoRow(
            label = stringResource(R.string.exercise_stats_range_volume),
            value = stringResource(R.string.exercise_stats_volume, formatKilograms(stats.volumeKg)),
        )
    }
}

@Composable
private fun WeightCard(points: List<ExerciseStatsPoint>, hasWeightData: Boolean) {
    SectionCard(
        title = stringResource(R.string.exercise_stats_chart_weight),
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        if (!hasWeightData) {
            Text(
                text = stringResource(R.string.exercise_stats_no_weight),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        ChartLegend(entries = weightLegend())
        WeightProgressChart(points = points)
    }
}

@Composable
private fun VolumeCard(points: List<ExerciseStatsPoint>) {
    SectionCard(
        title = stringResource(R.string.exercise_stats_chart_volume),
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        VolumeProgressChart(points = points)
    }
}

@Composable
private fun PerformancesCard(points: List<ExerciseStatsPoint>) {
    SectionCard(
        title = stringResource(R.string.exercise_stats_performances),
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderCell(stringResource(R.string.exercise_stats_table_date), DATE_WEIGHT)
            HeaderCell(stringResource(R.string.exercise_stats_table_sets), NUMBER_WEIGHT, TextAlign.End)
            HeaderCell(stringResource(R.string.exercise_stats_table_weight), WEIGHT_WEIGHT, TextAlign.End)
            HeaderCell(stringResource(R.string.exercise_stats_table_reps), NUMBER_WEIGHT, TextAlign.End)
            HeaderCell(stringResource(R.string.exercise_stats_table_rest), REST_WEIGHT, TextAlign.End)
        }
        HorizontalDivider()
        // Od najnowszych: „co robiłem ostatnio” to najczęstsze pytanie do tabeli.
        points.asReversed().forEach { point -> PerformanceRow(point) }
    }
}

@Composable
private fun PerformanceRow(point: ExerciseStatsPoint) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ValueCell(point.date.isoText(), DATE_WEIGHT)
        ValueCell(point.sets.toString(), NUMBER_WEIGHT, TextAlign.End)
        ValueCell(
            text = point.topSetKg?.let { kg ->
                stringResource(R.string.exercise_stats_weight, kg.toPlainStringOrNull().orEmpty())
            } ?: NO_VALUE,
            weight = WEIGHT_WEIGHT,
            align = TextAlign.End,
        )
        ValueCell(point.totalReps.toString(), NUMBER_WEIGHT, TextAlign.End)
        ValueCell(
            text = point.restAverageSeconds?.let { seconds ->
                formatElapsed(seconds * 1_000L)
            } ?: NO_VALUE,
            weight = REST_WEIGHT,
            align = TextAlign.End,
        )
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RowScope.ValueCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        textAlign = align,
        modifier = Modifier.weight(weight),
    )
}

private const val DATE_WEIGHT = 1.4f
private const val NUMBER_WEIGHT = 0.7f
private const val WEIGHT_WEIGHT = 1f
private const val REST_WEIGHT = 0.9f
private const val NO_VALUE = "—"
