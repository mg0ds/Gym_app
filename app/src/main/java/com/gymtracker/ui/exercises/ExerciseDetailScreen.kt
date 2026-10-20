package com.gymtracker.ui.exercises

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.gymtracker.R
import com.gymtracker.domain.model.Exercise
import com.gymtracker.domain.model.ExerciseMedia
import com.gymtracker.domain.model.ExerciseUsage
import com.gymtracker.domain.model.MediaKind
import com.gymtracker.domain.repository.MediaRejection
import com.gymtracker.domain.stats.ExerciseStats
import com.gymtracker.ui.components.InfoRow
import com.gymtracker.ui.components.SectionCard
import com.gymtracker.ui.components.openExternalLink
import com.gymtracker.ui.components.restHistorySummary
import com.gymtracker.ui.history.ChartLegend
import com.gymtracker.ui.history.WeightProgressChart
import com.gymtracker.ui.history.hasWeightRange
import com.gymtracker.ui.history.lastSetText
import com.gymtracker.ui.history.recordText
import com.gymtracker.ui.history.weightLegend
import com.gymtracker.ui.media.label
import java.io.File

/**
 * Szczegóły ćwiczenia: dane z katalogu, kroki wykonania, zdjęcia i filmy
 * z galerii, film, źródło i komentarz. Karta historii pokazuje rekord, ostatnie
 * wykonanie i średnią przerwę; pełne statystyki (wykresy, tabela) mają osobny
 * ekran.
 */
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenStats: (Long) -> Unit,
    onOpenMedia: (exerciseId: Long, mediaId: Long) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val exercise = uiState.exercise

    // Galeria systemowa (Photo Picker): bierzemy z niej plik, ale go nie
    // kopiujemy - zapamiętujemy tylko wskazanie i własną miniaturę.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { picked -> viewModel.linkMedia(picked.toString()) }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { picked -> viewModel.linkMedia(picked.toString()) }
    }

    ExerciseDetailScaffold(
        exercise = exercise,
        stats = stats,
        media = media,
        isLoading = uiState.isLoading,
        thumbnailFor = viewModel::thumbnailFile,
        onBack = onBack,
        onToggleFavourite = viewModel::toggleFavourite,
        onEdit = { exercise?.let { onEdit(it.id) } },
        onOpenStats = { exercise?.let { onOpenStats(it.id) } },
        onOpenMedia = onOpenMedia,
        onDeleteRequest = { showDeleteDialog = true },
        onAddPhoto = {
            pickPhoto.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onAddVideo = {
            pickVideo.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
            )
        },
        // Linki otwieramy poza aplikacją (YouTube albo przeglądarka).
        onOpenLink = { url -> openExternalLink(context, url) },
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

    uiState.mediaRejection?.let { reason ->
        MediaRejectionDialog(reason = reason, onDismiss = viewModel::dismissMediaRejection)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailScaffold(
    exercise: Exercise?,
    stats: ExerciseStats,
    media: List<ExerciseMedia>,
    isLoading: Boolean,
    thumbnailFor: (ExerciseMedia) -> File?,
    onBack: () -> Unit,
    onToggleFavourite: () -> Unit,
    onEdit: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenMedia: (Long, Long) -> Unit,
    onDeleteRequest: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddVideo: () -> Unit,
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
                media = media,
                thumbnailFor = thumbnailFor,
                innerPadding = innerPadding,
                onOpenStats = onOpenStats,
                onOpenMedia = onOpenMedia,
                onAddPhoto = onAddPhoto,
                onAddVideo = onAddVideo,
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
    media: List<ExerciseMedia>,
    thumbnailFor: (ExerciseMedia) -> File?,
    innerPadding: PaddingValues,
    onOpenStats: () -> Unit,
    onOpenMedia: (Long, Long) -> Unit,
    onAddPhoto: () -> Unit,
    onAddVideo: () -> Unit,
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
                        value = lastSetText(last, exercise.tracking),
                    )
                }
                if (stats.rest.hasSamples) {
                    InfoRow(
                        label = stringResource(R.string.exercise_stats_record_rests),
                        value = restHistorySummary(stats.rest),
                    )
                }
                if (stats.hasWeightData) {
                    ChartLegend(entries = weightLegend(showLightest = hasWeightRange(stats.points)))
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

        // Kroki pokazujemy tylko, gdy są - pusta sekcja „jak to zrobić” byłaby
        // obietnicą bez treści, a formularz i tak zawsze ma to pole.
        if (exercise.instructions.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.exercise_section_instructions)) {
                exercise.instructions.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${index + 1}.",
                            modifier = Modifier.width(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.exercise_section_media)) {
            if (media.isEmpty() && exercise.youtubeUrl == null && exercise.sourceUrl == null) {
                Text(
                    text = stringResource(R.string.media_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (media.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    media.forEach { item ->
                        MediaTile(
                            media = item,
                            thumbnail = thumbnailFor(item),
                            onClick = { onOpenMedia(exercise.id, item.id) },
                        )
                    }
                }
            }

            exercise.youtubeUrl?.let { url ->
                Button(onClick = { onOpenLink(url) }) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onAddPhoto) {
                    Text(stringResource(R.string.media_add_photo))
                }
                OutlinedButton(onClick = onAddVideo) {
                    Text(stringResource(R.string.media_add_video))
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

/**
 * Kafelek załącznika. Pokazujemy **własną miniaturę**, a nie plik z galerii:
 * dzięki temu siatka rysuje się od razu i tak samo po restarcie urządzenia,
 * nawet gdy dostęp do oryginału już wygasł. Film poznaczamy ikoną odtwarzania -
 * sama klatka nie mówi, że coś się rusza.
 */
@Composable
private fun MediaTile(
    media: ExerciseMedia,
    thumbnail: File?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(MEDIA_TILE_SIZE),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Opis dla czytnika ekranu: podpis, a gdy go nie ma - nazwa rodzaju.
        val label = media.caption?.takeIf { it.isNotBlank() } ?: media.kind.label()
        Box(
            modifier = Modifier
                .size(MEDIA_TILE_SIZE)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (media.kind == MediaKind.VIDEO) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            } else if (thumbnail == null) {
                // Bez miniatury zostaje sam napis: `material-icons-core` nie ma
                // ikony zdjęcia, a dokładanie całego `material-icons-extended`
                // dla jednego glifu byłoby przesadą.
                Text(
                    text = stringResource(R.string.media_kind_photo),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        media.caption?.let { caption ->
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MediaRejectionDialog(
    reason: MediaRejection,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.media_rejected_title)) },
        text = {
            Text(
                stringResource(
                    when (reason) {
                        MediaRejection.UNREADABLE -> R.string.media_rejected_unreadable
                        MediaRejection.UNSUPPORTED -> R.string.media_rejected_unsupported
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
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

/** Bok kafelka w siatce załączników - trzy w rzędzie na typowym telefonie. */
private val MEDIA_TILE_SIZE = 104.dp
