package com.gymtracker.ui.exercises

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.ExerciseVocabulary

/**
 * Formularz dodawania i edycji ćwiczenia. Ten sam ekran obsługuje oba przypadki -
 * różni je tylko tytuł i to, czy zapis tworzy nowy wiersz, czy aktualizuje istniejący.
 */
@Composable
fun ExerciseEditScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: ExerciseEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ExerciseEditContent(
        uiState = uiState,
        onBack = onBack,
        onSave = { viewModel.save(onSaved) },
        onNameChange = viewModel::onNameChange,
        onMuscleGroupChange = viewModel::onMuscleGroupChange,
        onEquipmentChange = viewModel::onEquipmentChange,
        onMechanicsChange = viewModel::onMechanicsChange,
        onLevelChange = viewModel::onLevelChange,
        onSecondaryMuscleToggle = viewModel::onSecondaryMuscleToggle,
        onYoutubeUrlChange = viewModel::onYoutubeUrlChange,
        onNotesChange = viewModel::onNotesChange,
        onSetsChange = viewModel::onSetsChange,
        onRepsMinChange = viewModel::onRepsMinChange,
        onRepsMaxChange = viewModel::onRepsMaxChange,
        onRestSecondsChange = viewModel::onRestSecondsChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseEditContent(
    uiState: ExerciseEditUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onMuscleGroupChange: (String?) -> Unit,
    onEquipmentChange: (String?) -> Unit,
    onMechanicsChange: (String?) -> Unit,
    onLevelChange: (String?) -> Unit,
    onSecondaryMuscleToggle: (String) -> Unit,
    onYoutubeUrlChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSetsChange: (String) -> Unit,
    onRepsMinChange: (String) -> Unit,
    onRepsMaxChange: (String) -> Unit,
    onRestSecondsChange: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (uiState.isNew) R.string.exercise_new_title else R.string.exercise_edit_title,
                        ),
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
                    TextButton(onClick = onSave, enabled = !uiState.isLoading) {
                        Text(stringResource(R.string.action_save))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.exercise_field_name)) },
                isError = uiState.isNameInvalid,
                supportingText = if (uiState.isNameInvalid) {
                    { Text(stringResource(R.string.exercise_field_name_required)) }
                } else {
                    null
                },
                singleLine = true,
            )

            ChipSelector(
                title = stringResource(R.string.exercise_field_muscle_group),
                options = ExerciseVocabulary.muscleGroups,
                selected = uiState.muscleGroup,
                labelFor = { muscle -> muscleGroupLabel(muscle).orEmpty() },
                onSelect = onMuscleGroupChange,
            )

            ChipSelector(
                title = stringResource(R.string.exercise_field_equipment),
                options = ExerciseVocabulary.equipment,
                selected = uiState.equipment,
                labelFor = { equipment -> equipmentLabel(equipment).orEmpty() },
                onSelect = onEquipmentChange,
            )

            ChipSelector(
                title = stringResource(R.string.exercise_field_mechanics),
                options = ExerciseVocabulary.mechanics,
                selected = uiState.mechanics,
                labelFor = { mechanics -> mechanicsLabel(mechanics).orEmpty() },
                onSelect = onMechanicsChange,
            )

            ChipSelector(
                title = stringResource(R.string.exercise_field_level),
                options = ExerciseVocabulary.levels,
                selected = uiState.level,
                labelFor = { level -> levelLabel(level).orEmpty() },
                onSelect = onLevelChange,
            )

            MultiChipSelector(
                title = stringResource(R.string.exercise_field_secondary),
                options = ExerciseVocabulary.muscleGroups,
                selected = uiState.secondaryMuscles,
                labelFor = { muscle -> muscleGroupLabel(muscle).orEmpty() },
                onToggle = onSecondaryMuscleToggle,
            )

            OutlinedTextField(
                value = uiState.youtubeUrl,
                onValueChange = onYoutubeUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.exercise_field_youtube)) },
                supportingText = if (uiState.youtubeUrl.isBlank()) {
                    null
                } else {
                    {
                        Text(
                            stringResource(
                                if (uiState.youtubeLinkRecognized) {
                                    R.string.exercise_field_youtube_recognized
                                } else {
                                    R.string.exercise_field_youtube_other
                                },
                            ),
                        )
                    }
                },
                singleLine = true,
            )

            Text(
                text = stringResource(R.string.exercise_field_defaults),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NumberField(
                    value = uiState.sets,
                    onValueChange = onSetsChange,
                    label = stringResource(R.string.exercise_field_sets),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = uiState.repsMin,
                    onValueChange = onRepsMinChange,
                    label = stringResource(R.string.exercise_field_reps_min),
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    value = uiState.repsMax,
                    onValueChange = onRepsMaxChange,
                    label = stringResource(R.string.exercise_field_reps_max),
                    modifier = Modifier.weight(1f),
                )
            }

            NumberField(
                value = uiState.restSeconds,
                onValueChange = onRestSecondsChange,
                label = stringResource(R.string.exercise_field_rest),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.exercise_field_notes)) },
                minLines = 3,
            )
        }
    }
}

@Composable
private fun ChipSelector(
    title: String,
    options: List<String>,
    selected: String?,
    labelFor: @Composable (String) -> String,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    // Powtórne kliknięcie w tę samą wartość czyści wybór.
                    onClick = { onSelect(if (selected == option) null else option) },
                    label = { Text(labelFor(option)) },
                )
            }
        }
    }
}

@Composable
private fun MultiChipSelector(
    title: String,
    options: List<String>,
    selected: List<String>,
    labelFor: @Composable (String) -> String,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldLabel(title)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    label = { Text(labelFor(option)) },
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}
