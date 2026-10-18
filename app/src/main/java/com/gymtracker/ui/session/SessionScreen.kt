package com.gymtracker.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.RepsUnit
import com.gymtracker.domain.model.RestInterval
import com.gymtracker.domain.model.SessionExercise
import com.gymtracker.domain.model.SetLog
import com.gymtracker.domain.model.SetLogDraft
import com.gymtracker.domain.model.WorkoutSession
import com.gymtracker.domain.stats.RestStats
import com.gymtracker.domain.timer.ActiveRest
import com.gymtracker.domain.timer.TrackedWorkout
import com.gymtracker.domain.timer.WorkoutTimerState
import com.gymtracker.domain.timer.formatElapsed
import com.gymtracker.ui.plans.targetText
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ekran treningu: zegar, notatka i logowanie serii.
 *
 * Wyjście strzałką **nie kończy** treningu - wraca się do niego z karty
 * „Kontynuuj trening” na kalendarzu, a zegar (i notyfikacja) idą dalej.
 * Zakończenie i przerwanie są osobnymi przyciskami, żeby przez przypadek nie
 * skasować trwającego treningu.
 */
@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onAddExercise: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SessionContent(
        uiState = uiState,
        onBack = onBack,
        onPause = viewModel::pause,
        onResume = viewModel::resume,
        onSaveNote = viewModel::saveNote,
        onAddSet = viewModel::addSet,
        onUpdateSet = viewModel::updateSet,
        onDeleteSet = viewModel::deleteSet,
        onEditRest = viewModel::setExerciseRest,
        onExtendRest = viewModel::extendRest,
        onShrinkRest = viewModel::shrinkRest,
        onSkipRest = viewModel::skipRest,
        onRestDue = viewModel::restDue,
        onFinish = { viewModel.finishSession(onFinished) },
        onCancel = { viewModel.cancelSession(onFinished) },
        onAddExercise = onAddExercise,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionContent(
    uiState: SessionUiState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSaveNote: (String) -> Unit,
    onAddSet: (sessionExerciseId: Long, draft: SetLogDraft) -> Unit,
    onUpdateSet: (setLogId: Long, draft: SetLogDraft) -> Unit,
    onDeleteSet: (setLogId: Long) -> Unit,
    onEditRest: (sessionExerciseId: Long, restSeconds: Int?) -> Unit,
    onExtendRest: () -> Unit,
    onShrinkRest: () -> Unit,
    onSkipRest: () -> Unit,
    onRestDue: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onAddExercise: () -> Unit,
) {
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var setDialog by remember { mutableStateOf<SetDialogTarget?>(null) }
    var restDialog by remember { mutableStateOf<SessionExercise?>(null) }
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
            if (session != null && uiState.isActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.session_finish))
                    }
                    // Porzucenie treningu nie zapisuje niczego, więc ma kolor
                    // błędu - nie wygląda jak zwykłe, bezpieczne wyjście.
                    TextButton(
                        onClick = { showCancelConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(text = stringResource(R.string.session_cancel))
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
            SessionTimerHeader(
                session = session,
                tracked = uiState.tracked,
                isActive = uiState.isActive,
                rests = uiState.rests,
                onPause = onPause,
                onResume = onResume,
            )

            // Przerwa jest najbardziej „działającą” rzeczą na ekranie, więc
            // stoi nad notatką: widać ją i można ją skorygować bez przewijania.
            uiState.rest?.takeIf { uiState.isActive }?.let { rest ->
                SessionRestBar(
                    rest = rest,
                    onShrink = onShrinkRest,
                    onExtend = onExtendRest,
                    onSkip = onSkipRest,
                    onDue = onRestDue,
                )
            }

            SessionNoteCard(
                note = session.note,
                editable = uiState.isActive,
                onEdit = { showNoteDialog = true },
            )

            if (uiState.exercises.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_no_exercises),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                uiState.exercises.forEachIndexed { index, exercise ->
                    SessionExerciseCard(
                        position = index + 1,
                        exercise = exercise,
                        restStats = uiState.restStats[exercise.exerciseId],
                        defaultRestSeconds = uiState.defaultRestSeconds,
                        editable = uiState.isActive,
                        onAddSet = {
                            setDialog = SetDialogTarget(exercise = exercise, log = null)
                        },
                        onEditSet = { log ->
                            setDialog = SetDialogTarget(exercise = exercise, log = log)
                        },
                        onEditRest = { restDialog = exercise },
                    )
                }
            }

            // Dopisywanie ćwiczeń w trakcie jest tym, czym żyje trening „od
            // zera” - bez tego nowy trening nie miałby jak zacząć.
            if (uiState.isActive) {
                TextButton(
                    onClick = onAddExercise,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = stringResource(R.string.session_add_exercise))
                }
            }
        }
    }

    setDialog?.let { target ->
        SetLogDialog(
            exerciseName = target.exercise.exerciseName,
            unit = target.exercise.repsUnit,
            existing = target.log,
            suggestion = SetLogSuggestion.of(
                previous = target.exercise.logs.lastOrNull(),
                unit = target.exercise.repsUnit,
                targetReps = target.exercise.repsMin,
            ),
            onDismiss = { setDialog = null },
            onConfirm = { draft ->
                if (target.log == null) {
                    onAddSet(target.exercise.id, draft)
                } else {
                    onUpdateSet(target.log.id, draft)
                }
                setDialog = null
            },
            onDelete = target.log?.let { log ->
                {
                    onDeleteSet(log.id)
                    setDialog = null
                }
            },
        )
    }

    if (showNoteDialog) {
        SessionNoteDialog(
            initialNote = session?.note.orEmpty(),
            onDismiss = { showNoteDialog = false },
            onSave = { text ->
                onSaveNote(text)
                showNoteDialog = false
            },
        )
    }

    restDialog?.let { exercise ->
        SessionRestDialog(
            exerciseName = exercise.exerciseName,
            initialSeconds = exercise.restTargetSeconds(uiState.defaultRestSeconds),
            onDismiss = { restDialog = null },
            onSave = { seconds ->
                onEditRest(exercise.id, seconds)
                restDialog = null
            },
        )
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
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
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

/** Cel kliknięcia w listę serii: ćwiczenie i seria (albo `null` = nowa). */
private data class SetDialogTarget(
    val exercise: SessionExercise,
    val log: SetLog?,
)

@Composable
private fun SessionTimerHeader(
    session: WorkoutSession,
    tracked: TrackedWorkout?,
    isActive: Boolean,
    rests: List<RestInterval>,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val isPaused = isActive && isSessionPaused(session, tracked)
    val frozen = isPaused || session.finishedAtEpochMs != null
    val nowMs = rememberTickingNow(frozen = frozen)
    val elapsedMs = sessionElapsedMs(session, tracked, nowMs)
    val restTotalMs = rests.sumOf { interval -> interval.elapsedMs(nowMs) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        when {
                            !isActive -> R.string.session_finished
                            isPaused -> R.string.session_pause
                            else -> R.string.session_in_progress
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (isActive) {
                    if (isPaused) {
                        Button(onClick = onResume) {
                            Text(text = stringResource(R.string.session_resume))
                        }
                    } else {
                        OutlinedButton(onClick = onPause) {
                            Text(text = stringResource(R.string.session_pause))
                        }
                    }
                }
            }
            Text(
                text = formatElapsed(elapsedMs),
                style = MaterialTheme.typography.headlineMedium,
            )
            // Druga liczba dopiero wtedy, gdy jest co pokazać: przerwy to część
            // treningu, ale nie każdy trening je ma.
            if (restTotalMs > 0 || rests.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.session_rest_under_bar,
                        formatElapsed((elapsedMs - restTotalMs).coerceAtLeast(0L)),
                        formatElapsed(restTotalMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isPaused) {
                Text(
                    text = stringResource(R.string.session_paused_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.session_started_at,
                    formatTimeOfDay(session.startedAtEpochMs),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Pasek przerwy: ile zostało, ile wynosi cel i trzy decyzje użytkownika.
 *
 * Gdy licznik dojdzie do zera, ekran sam zgłasza koniec przerwy - bez tego
 * odliczanie czekałoby na budzik, który przy braku zgody na dokładne alarmy
 * może się spóźnić o minuty.
 */
@Composable
private fun SessionRestBar(
    rest: ActiveRest,
    onShrink: () -> Unit,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    onDue: () -> Unit,
) {
    val nowMs = rememberTickingNow(frozen = false)
    val remainingMs = rest.remainingMs(nowMs)
    if (remainingMs == 0L) {
        LaunchedEffect(rest.intervalId) { onDue() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.session_rest_title, rest.exerciseName),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = formatElapsed(remainingMs),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(
                    R.string.session_rest_target,
                    formatElapsed(rest.plannedSeconds * 1_000L),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onShrink) {
                    Text(text = stringResource(R.string.session_rest_shrink))
                }
                OutlinedButton(onClick = onExtend) {
                    Text(text = stringResource(R.string.session_rest_extend))
                }
                TextButton(onClick = onSkip) {
                    Text(text = stringResource(R.string.session_rest_skip))
                }
            }
        }
    }
}

/**
 * Cel przerwy dla jednego ćwiczenia.
 *
 * Dialog dostaje **efektywną** wartość (plan → karta ćwiczenia → ustawienie
 * globalne), żeby użytkownik widział, ile faktycznie odlicza, a nie puste pole.
 */
@Composable
private fun SessionRestDialog(
    exerciseName: String,
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
) {
    var text by remember { mutableStateOf(initialSeconds.toString()) }
    val seconds = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.session_rest_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.session_rest_edit_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter { char -> char.isDigit() } },
                    label = { Text(text = stringResource(R.string.session_rest_seconds_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(seconds) },
                enabled = seconds != null && seconds > 0,
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(null) }) {
                    Text(text = stringResource(R.string.session_rest_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun SessionNoteCard(
    note: String?,
    editable: Boolean,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(if (editable) Modifier.clickable(onClick = onEdit) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.session_note_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = note ?: stringResource(R.string.session_note_add),
                style = MaterialTheme.typography.bodyMedium,
                color = if (note == null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun SessionNoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.session_note_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value },
                placeholder = { Text(text = stringResource(R.string.session_note_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (initialNote.isNotEmpty()) {
                    TextButton(onClick = { onSave("") }) {
                        Text(text = stringResource(R.string.session_note_clear))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun SessionExerciseCard(
    position: Int,
    exercise: SessionExercise,
    restStats: RestStats?,
    defaultRestSeconds: Int,
    editable: Boolean,
    onAddSet: () -> Unit,
    onEditSet: (SetLog) -> Unit,
    onEditRest: () -> Unit,
) {
    val target = targetText(exercise.targetSets, exercise.repsMin, exercise.repsMax, exercise.repsUnit)
    val restTarget = exercise.restTargetSeconds(defaultRestSeconds)
    val sessionRests = exercise.logs.mapNotNull { log -> log.restAfterS }.filter { seconds -> seconds > 0 }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.exerciseName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = listOfNotNull(
                            target,
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
                SetProgressChip(exercise = exercise)
            }

            RestTargetChip(
                seconds = restTarget,
                editable = editable,
                onClick = onEditRest,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (exercise.logs.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_sets_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                exercise.logs.forEach { log ->
                    SessionSetRow(
                        log = log,
                        unit = exercise.repsUnit,
                        editable = editable,
                        onClick = { onEditSet(log) },
                    )
                }
            }

            if (sessionRests.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.session_rest_session_summary,
                        sessionRests.size,
                        formatElapsed((sessionRests.average() * 1_000L).toLong()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            restStats?.takeIf { stats -> stats.hasSamples }?.let { stats ->
                Text(
                    text = restHistorySummary(stats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (editable) {
                TextButton(onClick = onAddSet) {
                    Text(text = stringResource(R.string.session_set_add))
                }
            }
        }
    }
}

/** „przerwa 1:30” - klikalna, gdy trening trwa; pokazuje efektywny cel. */
@Composable
private fun RestTargetChip(
    seconds: Int,
    editable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.session_rest_chip, formatElapsed(seconds * 1_000L)),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * „Średnia przerwa: 1:45 • ostatnio 2:05 (12 treningów)”.
 *
 * Liczba treningów idzie przez `plurals`, bo polski ma trzy formy mnogie -
 * sklejanie tego w jednym napisie dałoby „1 treningów”.
 */
@Composable
private fun restHistorySummary(stats: RestStats): String {
    val average = formatElapsed((stats.averageSeconds ?: 0) * 1_000L)
    val last = formatElapsed((stats.lastSeconds ?: 0) * 1_000L)
    val workouts = pluralStringResource(
        R.plurals.session_rest_sessions,
        stats.sessions,
        stats.sessions,
    )
    return stringResource(R.string.session_rest_history_summary, average, last) + " ($workouts)"
}

/** „2 z 5” albo sama liczba serii, gdy plan nie mówi, ile ich ma być. */
@Composable
private fun SetProgressChip(exercise: SessionExercise) {
    val logged = exercise.logs.size
    if (logged == 0 && exercise.targetSets == null) return
    val label = if (exercise.targetSets != null) {
        stringResource(R.string.session_sets_progress, logged, exercise.targetSets)
    } else {
        pluralStringResource(R.plurals.session_sets_count, logged, logged)
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SessionSetRow(
    log: SetLog,
    unit: RepsUnit,
    editable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (editable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = log.setIndex.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = listOfNotNull(
                setSummary(log, unit),
                log.restAfterS
                    ?.takeIf { seconds -> seconds > 0 }
                    ?.let { seconds ->
                        stringResource(
                            R.string.session_set_rest_after,
                            formatElapsed(seconds * 1_000L),
                        )
                    },
            ).joinToString(separator = " • "),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (log.isWarmup) {
            Text(
                text = stringResource(R.string.session_set_warmup_tag),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Zegar ekranu: tyka tylko wtedy, gdy coś naprawdę się zmienia.
 *
 * Zakończony trening i pauza to wartości zamrożone - tykanie byłoby wtedy
 * kłamstwem, a nie odświeżaniem.
 */
@Composable
private fun rememberTickingNow(frozen: Boolean): Long {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(frozen) {
        while (!frozen) {
            nowMs = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    return nowMs
}

/** Czas treningu o [nowMs]: z zegara w pamięci albo z zamkniętej sesji. */
private fun sessionElapsedMs(
    session: WorkoutSession,
    tracked: TrackedWorkout?,
    nowMs: Long,
): Long {
    val finishedAt = session.finishedAtEpochMs
    if (finishedAt != null) {
        return (finishedAt - session.startedAtEpochMs - session.pausedTotalMs).coerceAtLeast(0L)
    }
    val timer = tracked?.takeIf { it.sessionId == session.id }?.timer
        ?: WorkoutTimerState(
            startedAtEpochMs = session.startedAtEpochMs,
            pausedTotalMs = session.pausedTotalMs,
            pausedAtEpochMs = session.pausedAtEpochMs,
        )
    return timer.elapsedMs(nowMs)
}

private fun isSessionPaused(session: WorkoutSession, tracked: TrackedWorkout?): Boolean =
    tracked?.takeIf { it.sessionId == session.id }?.timer?.isPaused
        ?: (session.pausedAtEpochMs != null)

/** Godzina startu w strefie urządzenia, np. „18:42”. */
private fun formatTimeOfDay(epochMs: Long): String = TIME_FORMATTER.format(
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
)

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val TICK_MS = 1_000L
