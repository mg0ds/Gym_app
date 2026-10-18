package com.gymtracker.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.ArchiveImpact
import com.gymtracker.domain.model.Exercise

/**
 * Archiwum ćwiczeń (M9): co zdjęto z katalogu, co można przywrócić i ile
 * kosztuje opróżnienie archiwum.
 *
 * Ekran pokazuje **cenę** kasowania, zanim użytkownik je potwierdzi. Ćwiczenie
 * w archiwum nadal trzyma swoje pozycje w planach i wpisy w historii treningów,
 * więc „posprzątane” archiwum to nie kosz na pliki, a decyzja o utracie części
 * historii - i tylko użytkownik może ją podjąć.
 */
@Composable
fun ExerciseArchiveScreen(
    onBack: () -> Unit,
    viewModel: ExerciseArchiveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExerciseArchiveContent(
        uiState = uiState,
        onBack = onBack,
        onRestore = viewModel::onRestore,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onEmptyRequested = viewModel::onEmptyRequested,
        onEmptyCancelled = viewModel::onEmptyCancelled,
        onEmptyConfirmed = viewModel::onEmptyConfirmed,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseArchiveContent(
    uiState: ExerciseArchiveUiState,
    onBack: () -> Unit,
    onRestore: (Exercise) -> Unit,
    onDeleteRequested: (Exercise) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onEmptyRequested: () -> Unit,
    onEmptyCancelled: () -> Unit,
    onEmptyConfirmed: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.archive_title)) },
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
                .padding(innerPadding),
        ) {
            Text(
                text = stringResource(R.string.archive_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            uiState.status?.let { status -> ArchiveStatusLine(status = status) }

            when {
                uiState.isLoading -> Unit

                uiState.exercises.isEmpty() -> ArchiveEmptyState()

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(items = uiState.exercises, key = { exercise -> exercise.id }) { exercise ->
                        ArchiveRow(
                            exercise = exercise,
                            onRestore = onRestore,
                            onDelete = onDeleteRequested,
                        )
                    }

                    item {
                        EmptyArchiveFooter(
                            impact = uiState.impact,
                            onEmptyRequested = onEmptyRequested,
                        )
                    }
                }
            }
        }
    }

    uiState.pendingDelete?.let { exercise ->
        DeletePermanentlyDialog(
            exercise = exercise,
            onDismiss = onDeleteCancelled,
            onConfirm = onDeleteConfirmed,
        )
    }

    if (uiState.pendingEmpty) {
        EmptyArchiveDialog(
            impact = uiState.impact,
            onDismiss = onEmptyCancelled,
            onConfirm = onEmptyConfirmed,
        )
    }
}

@Composable
private fun ArchiveRow(
    exercise: Exercise,
    onRestore: (Exercise) -> Unit,
    onDelete: (Exercise) -> Unit,
) {
    val subtitle = listOfNotNull(
        muscleGroupLabel(exercise.muscleGroup),
        equipmentLabel(exercise.equipment),
    ).joinToString(separator = " • ")

    ListItem(
        headlineContent = {
            Text(
                text = exercise.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onRestore(exercise) }) {
                    Text(text = stringResource(R.string.archive_restore))
                }
                IconButton(onClick = { onDelete(exercise) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.archive_delete),
                    )
                }
            }
        },
    )
}

/**
 * Stopka listy: co przepadnie razem z archiwum i przycisk, który to robi.
 * Liczby pochodzą z jednego zapytania liczącego całe archiwum, więc zmieniają
 * się same, gdy użytkownik coś w nim przywróci.
 */
@Composable
private fun EmptyArchiveFooter(
    impact: ArchiveImpact,
    onEmptyRequested: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (impact.touchesDataElsewhere) {
            ImpactLines(impact = impact, withExerciseCount = false)
        }
        OutlinedButton(
            onClick = onEmptyRequested,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.archive_empty_action))
        }
    }
}

@Composable
private fun ArchiveStatusLine(status: ArchiveStatus) {
    Text(
        text = when (status) {
            is ArchiveStatus.Restored -> stringResource(R.string.archive_restored, status.name)
            is ArchiveStatus.Deleted -> stringResource(R.string.archive_deleted, status.name)
            is ArchiveStatus.Emptied -> stringResource(R.string.archive_emptied, status.count)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ArchiveEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.archive_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.archive_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DeletePermanentlyDialog(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.archive_delete_title)) },
        text = { Text(text = stringResource(R.string.archive_delete_message, exercise.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun EmptyArchiveDialog(
    impact: ArchiveImpact,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.archive_empty_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.archive_empty_dialog_message))
                ImpactLines(impact = impact, withExerciseCount = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.archive_empty_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ImpactLines(
    impact: ArchiveImpact,
    withExerciseCount: Boolean,
) {
    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant

    if (withExerciseCount && impact.exercises > 0) {
        Text(
            text = pluralStringResource(
                id = R.plurals.archive_impact_exercises,
                count = impact.exercises,
                impact.exercises,
            ),
            style = style,
            color = color,
        )
    }
    if (impact.planPositions > 0) {
        Text(
            text = pluralStringResource(
                id = R.plurals.archive_impact_plans,
                count = impact.planPositions,
                impact.planPositions,
            ),
            style = style,
            color = color,
        )
    }
    if (impact.sessionPositions > 0) {
        Text(
            text = pluralStringResource(
                id = R.plurals.archive_impact_history,
                count = impact.sessionPositions,
                impact.sessionPositions,
            ),
            style = style,
            color = color,
        )
    }
    if (impact.media > 0) {
        Text(
            text = pluralStringResource(
                id = R.plurals.archive_impact_media,
                count = impact.media,
                impact.media,
            ),
            style = style,
            color = color,
        )
    }
}
