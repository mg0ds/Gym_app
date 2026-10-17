package com.gymtracker.ui.exercises

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.ExerciseUsage
import com.gymtracker.domain.stats.ExerciseStats
import com.gymtracker.ui.components.InfoRow
import com.gymtracker.ui.components.SectionCard
import com.gymtracker.ui.components.restHistorySummary
import com.gymtracker.ui.history.ChartLegend
import com.gymtracker.ui.history.WeightProgressChart
import com.gymtracker.ui.history.lastSetText
import com.gymtracker.ui.history.recordText
import com.gymtracker.ui.history.weightLegend

/**
 * Szczegóły ćwiczenia: dane z katalogu, domyślne serie/powtórzenia/przerwa,
 * film, źródło i komentarz. Karta historii pokazuje rekord, ostatnie wykonanie
 * i średnią przerwę; pełne statystyki (wykresy, tabela) mają osobny ekran.
 */
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenStats: (Long) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val exercise = uiState.exercise

    ExerciseDetailScaffold(
        exercise = exercise,
        stats = stats,
        isLoading = uiState.isLoading,
        onBack = onBack,
        onToggleFavourite = viewModel::toggleFavourite,
        onEdit = { exercise?.let { onEdit(it.id) } },
        onOpenStats = { exercise?.let { onOpenStats(it.id) } },
        onDeleteRequest = { showDeleteDialog = true },
        // Linki otwieramy poza aplikacją (YouTube albo przeglądarka).
        onOpenLink = { url -> openLink(context, url) },
    )

    if (showDeleteDialog && exercise != null) {
        DeleteExerciseDialog(
            exerciseName = exercise.name,
            usage = usage,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.archive(onArchived = onBack)
            },
        )
    }

    // Linki otwieramy poza aplikacją (YouTube albo przeglądarka).
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailScaffold(
    exercise: Exercise?,
    stats: ExerciseStats,
    isLoading: Boolean,
    onBack: () -> Unit,
    onToggleFavourite: () -> Unit,
    onEdit: () -> Unit,
    onOpenStats: () -> Unit,
    onDeleteRequest: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = exercise?.name ?: stringResource(R.string.exercise_detail_title),
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
                actions = {
                    if (exercise != null) {
                        IconButton(onClick = onToggleFavourite) {
                            Icon(
                                imageVector = if (exercise.isFavourite) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Filled.FavoriteBorder
                                },
                                contentDescription = stringResource(
                                    if (exercise.isFavourite) {
                                        R.string.exercise_favourite_remove
                                    } else {
                                        R.string.exercise_favourite_add
                                    },
                                ),
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                            )
                        }
                        IconButton(onClick = onDeleteRequest) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            exercise != null -> ExerciseDetails(
                exercise = exercise,
                stats = stats,
                innerPadding = innerPadding,
                onOpenStats = onOpenStats,
                onOpenLink = onOpenLink,
            )

            isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.exercise_not_found))
            }
        }
    }
}

@Composable
private fun ExerciseDetails(
    exercise: Exercise,
    stats: ExerciseStats,
    innerPadding: PaddingValues,
    onOpenStats: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.exercise_section_history)) {
            if (!stats.hasData) {
                Text(
                    text = stringResource(R.string.exercise_stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                stats.records.heaviestSet?.let { record ->
                    InfoRow(
                        label = stringResource(R.string.exercise_stats_record_heaviest),
                        value = recordText(record),
                    )
                }
                stats.points.lastOrNull()?.let { last ->
                    InfoRow(
                        label = stringResource(R.string.exercise_stats_last_set),
                        value = lastSetText(last),
                    )
                }
                if (stats.rest.hasSamples) {
                    InfoRow(
                        label = stringResource(R.string.exercise_stats_record_rests),
                        value = restHistorySummary(stats.rest),
                    )
                }
                if (stats.hasWeightData) {
                    ChartLegend(entries = weightLegend())
                    WeightProgressChart(points = stats.points)
                }
                TextButton(onClick = onOpenStats) {
                    Text(stringResource(R.string.exercise_stats_open))
                }
            }
        }

        SectionCard(title = stringResource(R.string.exercise_section_details)) {
            InfoRow(
                label = stringResource(R.string.exercise_label_muscle_group),
                value = muscleGroupLabel(exercise.muscleGroup),
            )
            InfoRow(
                label = stringResource(R.string.exercise_label_muscle_group_secondary),
                value = exercise.secondaryMuscles
                    .mapNotNull { muscle -> muscleGroupLabel(muscle) }
                    .distinct()
                    .joinToString(separator = ", ")
                    .takeIf { it.isNotEmpty() },
            )
            InfoRow(
                label = stringResource(R.string.exercise_label_equipment),
                value = equipmentLabel(exercise.equipment),
            )
            InfoRow(
                label = stringResource(R.string.exercise_label_mechanics),
                value = mechanicsLabel(exercise.mechanics),
            )
            InfoRow(
                label = stringResource(R.string.exercise_label_level),
                value = levelLabel(exercise.level),
            )
            InfoRow(
                label = stringResource(R.string.exercise_label_name_in_program),
                value = exercise.nameInProgram?.takeIf { it != exercise.name },
            )
        }

        SectionCard(title = stringResource(R.string.exercise_section_defaults)) {
            InfoRow(
                label = stringResource(R.string.exercise_label_sets),
                value = exercise.defaultSets?.toString(),
            )
            InfoRow(
                label = stringResource(R.string.exercise_label_reps),
                value = repsText(exercise.defaultRepsMin, exercise.defaultRepsMax),
            )
            InfoRow(
                label = stringResource(R.string.exercise_label_rest),
                value = exercise.defaultRestS?.let { seconds ->
                    stringResource(R.string.exercise_value_rest_seconds, seconds)
                },
            )
            if (exercise.defaultSets == null && exercise.defaultRepsMin == null &&
                exercise.defaultRepsMax == null && exercise.defaultRestS == null
            ) {
                Text(
                    text = stringResource(R.string.exercise_value_not_set),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (exercise.youtubeUrl != null || exercise.sourceUrl != null) {
            SectionCard(title = stringResource(R.string.exercise_section_media)) {
                exercise.youtubeUrl?.let { url ->
                    Button(onClick = { onOpenLink(url) }) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.exercise_open_video),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                exercise.sourceUrl?.let { url ->
                    TextButton(onClick = { onOpenLink(url) }) {
                        Text(stringResource(R.string.exercise_open_source))
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.exercise_section_notes)) {
            Text(
                text = exercise.notes?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.exercise_value_not_set),
                style = MaterialTheme.typography.bodyMedium,
                color = if (exercise.notes.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun DeleteExerciseDialog(
    exerciseName: String,
    usage: ExerciseUsage,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exercise_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.exercise_delete_message, exerciseName))
                // Usunięcie jest archiwizacją, więc plany i historia zostają -
                // mówimy to wprost, żeby nikt nie bał się stracić treningów.
                if (usage.planPositions > 0) {
                    Text(
                        text = pluralStringResource(
                            id = R.plurals.exercise_delete_used_in_plans,
                            count = usage.planPositions,
                            usage.planPositions,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (usage.sessionPositions > 0) {
                    Text(
                        text = pluralStringResource(
                            id = R.plurals.exercise_delete_used_in_sessions,
                            count = usage.sessionPositions,
                            usage.sessionPositions,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun repsText(repsMin: Int?, repsMax: Int?): String? = when {
    repsMin != null && repsMax != null && repsMin != repsMax ->
        stringResource(R.string.exercise_value_reps_range, repsMin, repsMax)

    repsMin != null -> stringResource(R.string.exercise_value_reps_single, repsMin)

    repsMax != null -> stringResource(R.string.exercise_value_reps_single, repsMax)

    else -> null
}

private fun openLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
}
