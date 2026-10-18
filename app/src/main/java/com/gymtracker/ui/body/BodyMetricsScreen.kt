package com.gymtracker.ui.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.model.MetricType

/**
 * Lista wskaźników: aktywne i zarchiwizowane, z dodawaniem i poprawianiem.
 *
 * Zarchiwizowane zostają na liście (z podpisem), bo inaczej nie byłoby jak ich
 * przywrócić - a „archiwizuj” nie znaczy „usuń na zawsze”.
 */
@Composable
fun BodyMetricsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BodyMetricsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BodyMetricsContent(
        uiState = uiState,
        onBack = onBack,
        onAddType = viewModel::addType,
        onUpdateType = viewModel::updateType,
        onArchivedChange = viewModel::setArchived,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyMetricsContent(
    uiState: BodyMetricsUiState,
    onBack: () -> Unit,
    onAddType: (String, String?, MetricDirection, Double?) -> Unit,
    onUpdateType: (Long, String?, String?, MetricDirection, Double?) -> Unit,
    onArchivedChange: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editedType by remember { mutableStateOf<MetricType?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.metric_metrics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editedType = null
                            dialogOpen = true
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.metric_add_type),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
        ) {
            items(items = uiState.types, key = { type -> type.id }) { type ->
                MetricRow(
                    type = type,
                    onClick = {
                        editedType = type
                        dialogOpen = true
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (dialogOpen) {
        MetricTypeDialog(
            initial = editedType,
            onDismiss = { dialogOpen = false },
            onSave = { name, unit, direction, target ->
                val current = editedType
                if (current == null) {
                    onAddType(requireNotNull(name), unit, direction, target)
                } else {
                    onUpdateType(current.id, name, unit, direction, target)
                }
                dialogOpen = false
            },
            onArchiveChange = { type, archived ->
                onArchivedChange(type.id, archived)
                dialogOpen = false
            },
        )
    }
}

@Composable
private fun MetricRow(type: MetricType, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = type.displayName(), style = MaterialTheme.typography.bodyLarge)
            val archivedTag = if (type.isArchived) {
                stringResource(R.string.metric_type_archived_tag)
            } else {
                null
            }
            val details = listOfNotNull(
                type.unit,
                stringResource(type.direction.labelRes()),
                archivedTag,
            )
            Text(
                text = details.joinToString(separator = " • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
