package com.gymtracker.ui.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.PlanDay
import com.gymtracker.domain.model.Recurrence
import com.gymtracker.ui.calendar.recurrenceSummary
import com.gymtracker.ui.components.ReorderableColumn
import com.gymtracker.ui.components.StartAnotherWorkoutDialog

/**
 * Plan: nazwa, opis i lista dni w kolejności. Dni przestawia się przeciągnięciem,
 * a stąd też **rozpoczyna się trening** z wybranego dnia (kryterium M2).
 *
 * Pod dniami jest harmonogram ([Recurrence]): reguły „co N dni”, które
 * rozplanowują dni planu w kalendarzu (M3).
 */
@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onOpenDay: (dayId: Long) -> Unit,
    onStartSchedule: (planId: Long) -> Unit,
    onEditSchedule: (planId: Long, recurrenceId: Long) -> Unit,
    onSessionStarted: (sessionId: Long) -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlanDetailContent(
        uiState = uiState,
        onBack = onBack,
        onOpenDay = onOpenDay,
        onAddDay = viewModel::addDay,
        onRenameDay = viewModel::renameDay,
        onDeleteDay = viewModel::deleteDay,
        onMoveDay = viewModel::moveDay,
        onUpdatePlan = viewModel::updatePlan,
        onDeletePlan = viewModel::deletePlan,
        onStartSession = { dayId -> viewModel.startSession(dayId, onSessionStarted) },
        onStartSchedule = onStartSchedule,
        onEditSchedule = onEditSchedule,
        onDeleteRecurrence = viewModel::deleteRecurrence,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDetailContent(
    uiState: PlanDetailUiState,
    onBack: () -> Unit,
    onOpenDay: (dayId: Long) -> Unit,
    onAddDay: (name: String, dayOfWeek: Int?) -> Unit,
    onRenameDay: (dayId: Long, name: String, dayOfWeek: Int?) -> Unit,
    onDeleteDay: (dayId: Long) -> Unit,
    onMoveDay: (fromIndex: Int, toIndex: Int) -> Unit,
    onUpdatePlan: (name: String, description: String) -> Unit,
    onDeletePlan: (onDeleted: () -> Unit) -> Unit,
    onStartSession: (dayId: Long) -> Unit,
    onStartSchedule: (planId: Long) -> Unit,
    onEditSchedule: (planId: Long, recurrenceId: Long) -> Unit,
    onDeleteRecurrence: (recurrenceId: Long) -> Unit,
) {
    var showAddDayDialog by remember { mutableStateOf(false) }
    var showEditPlanDialog by remember { mutableStateOf(false) }
    var showDeletePlanDialog by remember { mutableStateOf(false) }
    var dayToRename by remember { mutableStateOf<PlanDay?>(null) }
    var dayToDelete by remember { mutableStateOf<PlanDay?>(null) }
    var recurrenceToDelete by remember { mutableStateOf<Recurrence?>(null) }
    // Start dnia przerwie trwający trening („jeden trening naraz”), więc pytamy.
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun requestStart(dayId: Long) {
        if (uiState.activeSessionName == null) {
            onStartSession(dayId)
        } else {
            pendingStart = { onStartSession(dayId) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.plan?.name.orEmpty(),
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
                    IconButton(onClick = { showEditPlanDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.plan_edit_action),
                        )
                    }
                    IconButton(onClick = { showDeletePlanDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.plan_more_actions),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDayDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.plan_day_add),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            uiState.plan?.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            ReorderableColumn(
                itemCount = uiState.days.size,
                onMove = onMoveDay,
                contentPadding = PaddingValues(bottom = 96.dp),
                footer = {
                    if (uiState.days.isEmpty()) {
                        EmptyDays()
                    } else {
                        ScheduleSection(
                            planId = uiState.plan?.id,
                            recurrences = uiState.recurrences,
                            onStartSchedule = onStartSchedule,
                            onEditSchedule = onEditSchedule,
                            onDeleteRecurrence = { recurrence -> recurrenceToDelete = recurrence },
                        )
                    }
                },
            ) { index ->
                val day = uiState.days[index]
                DayCard(
                    day = day,
                    index = index,
                    dayCount = uiState.days.size,
                    onOpen = onOpenDay,
                    onStart = { dayId -> requestStart(dayId) },
                    onRename = { dayToRename = day },
                    onDelete = { dayToDelete = day },
                    onMoveUp = { onMoveDay(index, index - 1) },
                    onMoveDown = { onMoveDay(index, index + 1) },
                )
            }
        }
    }

    if (showAddDayDialog) {
        PlanDayDialog(
            onDismiss = { showAddDayDialog = false },
            onConfirm = { name, dayOfWeek ->
                showAddDayDialog = false
                onAddDay(name, dayOfWeek)
            },
        )
    }

    if (showEditPlanDialog) {
        val plan = uiState.plan
        if (plan != null) {
            PlanFormDialog(
                title = stringResource(R.string.plan_edit_action),
                confirmLabel = stringResource(R.string.action_save),
                initialName = plan.name,
                initialDescription = plan.description.orEmpty(),
                onDismiss = { showEditPlanDialog = false },
                onConfirm = { name, description ->
                    showEditPlanDialog = false
                    onUpdatePlan(name, description)
                },
            )
        }
    }

    dayToRename?.let { day ->
        PlanDayDialog(
            initialName = day.name,
            initialDayOfWeek = day.dayOfWeek,
            onDismiss = { dayToRename = null },
            onConfirm = { name, dayOfWeek ->
                dayToRename = null
                onRenameDay(day.id, name, dayOfWeek)
            },
        )
    }

    dayToDelete?.let { day ->
        ConfirmDeleteDialog(
            text = stringResource(R.string.plan_day_delete_confirm, day.name),
            onDismiss = { dayToDelete = null },
            onConfirm = {
                dayToDelete = null
                onDeleteDay(day.id)
            },
        )
    }

    recurrenceToDelete?.let { recurrence ->
        ConfirmDeleteDialog(
            text = stringResource(R.string.plan_schedule_delete_confirm),
            onDismiss = { recurrenceToDelete = null },
            onConfirm = {
                recurrenceToDelete = null
                onDeleteRecurrence(recurrence.id)
            },
        )
    }

    if (showDeletePlanDialog) {
        ConfirmDeleteDialog(
            text = stringResource(R.string.plan_delete_confirm, uiState.plan?.name.orEmpty()),
            onDismiss = { showDeletePlanDialog = false },
            onConfirm = {
                showDeletePlanDialog = false
                onDeletePlan(onBack)
            },
        )
    }

    pendingStart?.let { action ->
        StartAnotherWorkoutDialog(
            activeName = uiState.activeSessionName.orEmpty(),
            onConfirm = {
                pendingStart = null
                action()
            },
            onDismiss = { pendingStart = null },
        )
    }
}

@Composable
private fun DayCard(
    day: PlanDay,
    index: Int,
    dayCount: Int,
    onOpen: (dayId: Long) -> Unit,
    onStart: (dayId: Long) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val plannedCount = day.exercises.count { exercise -> !exercise.isAlternative }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onOpen(day.id) },
    ) {
        Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.plan_drag_hint),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        text = day.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        weekdayLabel(day.dayOfWeek),
                        pluralStringResource(
                            R.plurals.plan_day_exercise_count,
                            plannedCount,
                            plannedCount,
                        ),
                    ).joinToString(separator = " • ")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(onClick = { onStart(day.id) }) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                    Text(text = stringResource(R.string.plan_day_start))
                }

                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.plan_more_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_day_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_move_up)) },
                        enabled = index > 0,
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_move_down)) },
                        enabled = index < dayCount - 1,
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMoveDown()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_day_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDays() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_days_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.plan_days_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Harmonogram planu: reguły, które rozplanowują dni w kalendarzu. Stąd zakłada
 * się nową regułę („Zaplanuj treningi”) i stąd zmienia się lub usuwa istniejące.
 * Bez dni w planie nie ma czego rozplanowywać, więc sekcja wtedy nie występuje.
 */
@Composable
private fun ScheduleSection(
    planId: Long?,
    recurrences: List<Recurrence>,
    onStartSchedule: (planId: Long) -> Unit,
    onEditSchedule: (planId: Long, recurrenceId: Long) -> Unit,
    onDeleteRecurrence: (Recurrence) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.plan_schedule_title),
            style = MaterialTheme.typography.titleMedium,
        )

        if (recurrences.isEmpty()) {
            Text(
                text = stringResource(R.string.plan_schedule_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recurrences.forEach { recurrence ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = recurrenceSummary(recurrence),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { planId?.let { id -> onEditSchedule(id, recurrence.id) } },
                            ) {
                                Text(text = stringResource(R.string.plan_schedule_edit))
                            }
                            TextButton(onClick = { onDeleteRecurrence(recurrence) }) {
                                Text(text = stringResource(R.string.plan_schedule_delete))
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = { planId?.let(onStartSchedule) }) {
            Text(text = stringResource(R.string.plan_schedule_action))
        }
    }
}
