package com.gymtracker.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.ui.plans.restText
import com.gymtracker.ui.plans.targetText
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Trening rozpoczęty z planu: co jest do zrobienia i jak długo już trwa.
 *
 * Wyjście strzałką **nie kończy** treningu - wraca się do niego z ekranu „Dziś”
 * („Kontynuuj trening”). Zakończenie i przerwanie są osobnymi przyciskami,
 * żeby przez przypadek nie skasować trwającego treningu.
 */
@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SessionContent(
        uiState = uiState,
        onBack = onBack,
        onFinish = { viewModel.finishSession(onFinished) },
        onCancel = { viewModel.cancelSession(onFinished) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionContent(
    uiState: SessionUiState,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
) {
    var showCancelConfirm by remember { mutableStateOf(false) }
    val session = uiState.session

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = session?.name.orEmpty(),
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
        bottomBar = {
            if (session != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (uiState.isActive) {
                        Button(
                            onClick = onFinish,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.session_finish))
                        }
                        TextButton(
                            onClick = { showCancelConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.session_cancel))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        if (session == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SessionHeader(
                startedAtEpochMs = session.startedAtEpochMs,
                finishedAtEpochMs = session.finishedAtEpochMs,
                isActive = uiState.isActive,
            )

            if (uiState.exercises.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_no_exercises),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                uiState.exercises.forEachIndexed { index, exercise ->
                    SessionExerciseRow(position = index + 1, exercise = exercise)
                }
            }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(text = stringResource(R.string.session_cancel_confirm_title)) },
            text = { Text(text = stringResource(R.string.session_cancel_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        onCancel()
                    },
                ) {
                    Text(text = stringResource(R.string.session_cancel))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SessionHeader(
    startedAtEpochMs: Long,
    finishedAtEpochMs: Long?,
    isActive: Boolean,
) {
    val elapsedMs = rememberElapsedMs(startedAtEpochMs, finishedAtEpochMs)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    if (isActive) R.string.session_in_progress else R.string.session_finished,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = formatDuration(elapsedMs),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(
                    R.string.session_started_at,
                    formatTimeOfDay(startedAtEpochMs),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SessionExerciseRow(
    position: Int,
    exercise: SessionExercise,
) {
    val target = targetText(exercise.sets, exercise.repsMin, exercise.repsMax, exercise.repsUnit)
    val rest = restText(exercise.restSeconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column {
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = listOfNotNull(
                        target,
                        rest,
                        exercise.supersetGroup?.let { groupNumber ->
                            stringResource(R.string.plan_superset_chip, groupNumber)
                        },
                    ).joinToString(separator = " • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                exercise.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Czas trwania od [startedAtEpochMs] do teraz (albo do [finishedAtEpochMs]).
 *
 * Zegar tyka w interfejsie; w M4 dojdzie usługa działająca w tle, ale sam
 * zapis czasu startu w bazie już teraz sprawia, że trening przeżywa obrót ekranu
 * i powrót do aplikacji.
 */
@Composable
private fun rememberElapsedMs(startedAtEpochMs: Long, finishedAtEpochMs: Long?): Long {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(startedAtEpochMs, finishedAtEpochMs) {
        while (finishedAtEpochMs == null) {
            nowMs = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }

    return ((finishedAtEpochMs ?: nowMs) - startedAtEpochMs).coerceAtLeast(0L)
}

/** „42:10” albo „1:05:12”. */
internal fun formatDuration(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Godzina startu w strefie urządzenia, np. „18:42”. */
private fun formatTimeOfDay(epochMs: Long): String = TIME_FORMATTER.format(
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
)

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val TICK_MS = 1_000L
