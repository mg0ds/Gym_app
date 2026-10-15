package com.gymtracker.ui.plans

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.gymtracker.domain.model.PlanExercise
import com.gymtracker.ui.components.InfoTag
import com.gymtracker.ui.components.ReorderableColumn

/**
 * Dzień planu: ćwiczenia w kolejności, ich cel (serie × powtórzenia/czas),
 * superserie i alternatywy („X albo Y”).
 *
 * Kolejność zmienia się przeciągnięciem (długie przytrzymanie) albo z menu
 * wiersza - menu zostaje jako dostępna alternatywa dla przeciągania.
 */
@Composable
fun PlanDayScreen(
    onBack: () -> Unit,
    onAddExercise: (dayId: Long) -> Unit,
    onAddAlternative: (dayId: Long, anchorPlanExerciseId: Long) -> Unit,
    viewModel: PlanDayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlanDayContent(
        uiState = uiState,
        onBack = onBack,
        onAddExercise = { onAddExercise(viewModel.dayId) },
        onAddAlternative = { anchorId -> onAddAlternative(viewModel.dayId, anchorId) },
        onMoveExercise = viewModel::moveExercise,
        onUpdateTargets = viewModel::updateTargets,
        onRemoveExercise = viewModel::removeExercise,
        onLinkSuperset = viewModel::linkSuperset,
        onUnlinkSuperset = viewModel::unlinkSuperset,
        onMakeDefaultChoice = viewModel::makeDefaultChoice,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDayContent(
    uiState: PlanDayUiState,
    onBack: () -> Unit,
    onAddExercise: () -> Unit,
    onAddAlternative: (anchorPlanExerciseId: Long) -> Unit,
    onMoveExercise: (fromIndex: Int, toIndex: Int) -> Unit,
    onUpdateTargets: (planExerciseId: Long, targets: com.gymtracker.domain.model.PlanExerciseTargets) -> Unit,
    onRemoveExercise: (planExerciseId: Long) -> Unit,
    onLinkSuperset: (firstId: Long, secondId: Long) -> Unit,
    onUnlinkSuperset: (planExerciseId: Long) -> Unit,
    onMakeDefaultChoice: (planExerciseId: Long) -> Unit,
) {
    var exerciseForTargets by remember { mutableStateOf<PlanExercise?>(null) }
    var exerciseToDelete by remember { mutableStateOf<PlanExercise?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.day?.name.orEmpty(),
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
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExercise) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.plan_day_add_exercise),
                )
            }
        },
    ) { innerPadding ->
        if (uiState.groups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.plan_day_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.plan_day_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        ReorderableColumn(
            itemCount = uiState.groups.size,
            onMove = onMoveExercise,
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        ) { index ->
            val group = uiState.groups[index]
            ExerciseGroupRow(
                group = group,
                index = index,
                groupCount = uiState.groups.size,
                nextPrimaryId = uiState.groups.getOrNull(index + 1)?.primary?.id,
                onEditTargets = { exercise -> exerciseForTargets = exercise },
                onDelete = { exercise -> exerciseToDelete = exercise },
                onAddAlternative = onAddAlternative,
                onMoveUp = { onMoveExercise(index, index - 1) },
                onMoveDown = { onMoveExercise(index, index + 1) },
                onLinkSuperset = onLinkSuperset,
                onUnlinkSuperset = onUnlinkSuperset,
                onMakeDefaultChoice = onMakeDefaultChoice,
            )
        }
    }

    exerciseForTargets?.let { exercise ->
        ExerciseTargetDialog(
            exercise = exercise,
            onDismiss = { exerciseForTargets = null },
            onConfirm = { targets ->
                exerciseForTargets = null
                onUpdateTargets(exercise.id, targets)
            },
        )
    }

    exerciseToDelete?.let { exercise ->
        ConfirmDeleteDialog(
            text = stringResource(R.string.plan_exercise_delete_confirm, exercise.exerciseName),
            onDismiss = { exerciseToDelete = null },
            onConfirm = {
                exerciseToDelete = null
                onRemoveExercise(exercise.id)
            },
        )
    }
}

@Composable
private fun ExerciseGroupRow(
    group: ExerciseGroup,
    index: Int,
    groupCount: Int,
    nextPrimaryId: Long?,
    onEditTargets: (PlanExercise) -> Unit,
    onDelete: (PlanExercise) -> Unit,
    onAddAlternative: (Long) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onLinkSuperset: (Long, Long) -> Unit,
    onUnlinkSuperset: (Long) -> Unit,
    onMakeDefaultChoice: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        ExerciseRow(
            exercise = group.primary,
            // Alternatywy mają własną numerację („1a”), bo są wariantem pozycji.
            positionLabel = (index + 1).toString(),
            isAlternative = false,
            onEditTargets = onEditTargets,
            onDelete = onDelete,
            onAddAlternative = { onAddAlternative(group.primary.id) },
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            moveUpEnabled = index > 0,
            moveDownEnabled = index < groupCount - 1,
            onLinkSuperset = { nextPrimaryId?.let { next -> onLinkSuperset(group.primary.id, next) } },
            linkSupersetEnabled = nextPrimaryId != null && group.primary.supersetGroup == null,
            onUnlinkSuperset = { onUnlinkSuperset(group.primary.id) },
            onMakeDefaultChoice = null,
        )

        group.alternatives.forEachIndexed { alternativeIndex, alternative ->
            ExerciseRow(
                exercise = alternative,
                positionLabel = "${index + 1}${'a' + alternativeIndex}",
                isAlternative = true,
                onEditTargets = onEditTargets,
                onDelete = onDelete,
                onAddAlternative = null,
                onMoveUp = {},
                onMoveDown = {},
                moveUpEnabled = false,
                moveDownEnabled = false,
                onLinkSuperset = null,
                linkSupersetEnabled = false,
                onUnlinkSuperset = {},
                onMakeDefaultChoice = { onMakeDefaultChoice(alternative.id) },
            )
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: PlanExercise,
    positionLabel: String,
    isAlternative: Boolean,
    onEditTargets: (PlanExercise) -> Unit,
    onDelete: (PlanExercise) -> Unit,
    onAddAlternative: (() -> Unit)?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    moveUpEnabled: Boolean,
    moveDownEnabled: Boolean,
    onLinkSuperset: (() -> Unit)?,
    linkSupersetEnabled: Boolean,
    onUnlinkSuperset: () -> Unit,
    onMakeDefaultChoice: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val target = targetText(exercise.sets, exercise.repsMin, exercise.repsMax, exercise.repsUnit)
    val rest = restText(exercise.restSeconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isAlternative) 32.dp else 16.dp,
                end = 16.dp,
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isAlternative) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.plan_drag_hint),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = positionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                if (isAlternative) {
                    Text(
                        text = stringResource(R.string.plan_alternative_prefix),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(target, rest).joinToString(separator = " • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    exercise.supersetGroup?.let { groupNumber ->
                        InfoTag(text = stringResource(R.string.plan_superset_chip, groupNumber))
                    }
                    if (!isAlternative && exercise.isDefaultChoice && exercise.alternativeGroup != null) {
                        InfoTag(text = stringResource(R.string.plan_default_choice_chip))
                    }
                }
            }

            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.plan_more_actions),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (onMakeDefaultChoice != null) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_make_default)) },
                        onClick = {
                            menuOpen = false
                            onMakeDefaultChoice()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.plan_edit_target)) },
                    onClick = {
                        menuOpen = false
                        onEditTargets(exercise)
                    },
                )
                if (onAddAlternative != null) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_add_alternative)) },
                        onClick = {
                            menuOpen = false
                            onAddAlternative()
                        },
                    )
                }
                if (onLinkSuperset != null) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_join_superset)) },
                        enabled = linkSupersetEnabled,
                        onClick = {
                            menuOpen = false
                            onLinkSuperset()
                        },
                    )
                }
                if (exercise.supersetGroup != null) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_unlink_superset)) },
                        onClick = {
                            menuOpen = false
                            onUnlinkSuperset()
                        },
                    )
                }
                if (!isAlternative) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_move_up)) },
                        enabled = moveUpEnabled,
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.plan_move_down)) },
                        enabled = moveDownEnabled,
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMoveDown()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.plan_remove_exercise)) },
                    onClick = {
                        menuOpen = false
                        onDelete(exercise)
                    },
                )
            }
        }
    }
}
