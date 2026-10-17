package com.gymtracker.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.data.prefs.SettingsDataStore
import com.gymtracker.domain.model.ThemeMode
import com.gymtracker.domain.timer.formatElapsed
import com.gymtracker.ui.theme.GymTrackerTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val seedImportState by viewModel.seedImportState.collectAsStateWithLifecycle()
    val programImportState by viewModel.programImportState.collectAsStateWithLifecycle()
    val defaultRestSeconds by viewModel.defaultRestSeconds.collectAsStateWithLifecycle()

    SettingsContent(
        themeMode = themeMode,
        seedImportState = seedImportState,
        programImportState = programImportState,
        defaultRestSeconds = defaultRestSeconds,
        onThemeModeChange = viewModel::setThemeMode,
        onImportSeedData = viewModel::importSeedData,
        onImportProgramData = viewModel::importProgramData,
        onDefaultRestChange = viewModel::setDefaultRestSeconds,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    themeMode: ThemeMode,
    seedImportState: SeedImportState,
    programImportState: ProgramImportState,
    defaultRestSeconds: Int,
    onThemeModeChange: (ThemeMode) -> Unit,
    onImportSeedData: () -> Unit,
    onImportProgramData: () -> Unit,
    onDefaultRestChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRestDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.settings_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(text = stringResource(R.string.settings_section_appearance))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme_mode),
                    style = MaterialTheme.typography.bodyLarge,
                )
                ThemeMode.entries.forEach { mode ->
                    ThemeModeRow(
                        mode = mode,
                        selected = mode == themeMode,
                        onSelect = { onThemeModeChange(mode) },
                    )
                }
            }

            SectionTitle(text = stringResource(R.string.settings_section_training))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRestDialog = true }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_default_rest),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = formatElapsed(defaultRestSeconds * 1_000L),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_default_rest_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(text = stringResource(R.string.settings_section_data))

            Text(
                text = stringResource(R.string.settings_import_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onImportSeedData,
                enabled = seedImportState != SeedImportState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.settings_import_action))
            }

            SeedImportStatus(state = seedImportState)

            Text(
                text = stringResource(R.string.settings_import_programs_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onImportProgramData,
                enabled = programImportState != ProgramImportState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.settings_import_programs_action))
            }

            ProgramImportStatus(state = programImportState)
        }
    }

    if (showRestDialog) {
        DefaultRestDialog(
            initialSeconds = defaultRestSeconds,
            onDismiss = { showRestDialog = false },
            onSave = { seconds ->
                onDefaultRestChange(seconds)
                showRestDialog = false
            },
        )
    }
}

@Composable
private fun DefaultRestDialog(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initialSeconds.toString()) }
    val seconds = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_default_rest_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { char -> char.isDigit() } },
                label = { Text(text = stringResource(R.string.settings_default_rest_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(seconds ?: initialSeconds) },
                enabled = seconds != null && seconds > 0,
            ) {
                Text(text = stringResource(R.string.action_save))
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SeedImportStatus(state: SeedImportState) {
    val text = when (state) {
        SeedImportState.Idle -> null
        SeedImportState.Running -> stringResource(R.string.settings_import_running)
        SeedImportState.Failed -> stringResource(R.string.settings_import_failed)
        is SeedImportState.Done -> stringResource(
            R.string.settings_import_done,
            state.imported,
            state.skipped,
        )
    } ?: return

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state is SeedImportState.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun ProgramImportStatus(state: ProgramImportState) {
    val text = when (state) {
        ProgramImportState.Idle -> null
        ProgramImportState.Running -> stringResource(R.string.settings_import_programs_running)
        ProgramImportState.Failed -> stringResource(R.string.settings_import_programs_failed)
        is ProgramImportState.Done -> stringResource(
            R.string.settings_import_programs_done,
            state.imported,
            state.skipped,
            state.days,
        )
    } ?: return

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state is ProgramImportState.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(mode.labelRes()))
    }
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    GymTrackerTheme(dynamicColor = false) {
        SettingsContent(
            themeMode = ThemeMode.SYSTEM,
            seedImportState = SeedImportState.Idle,
            programImportState = ProgramImportState.Idle,
            defaultRestSeconds = SettingsDataStore.DEFAULT_REST_SECONDS,
            onThemeModeChange = {},
            onImportSeedData = {},
            onImportProgramData = {},
            onDefaultRestChange = {},
        )
    }
}
