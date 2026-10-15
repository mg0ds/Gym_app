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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.Plan
import com.gymtracker.ui.components.InfoTag

/**
 * Lista planów treningowych: gotowy plan z danych startowych i własne plany
 * użytkownika. Plan otwiera się na ekranie dni, gdzie układa się jego treść.
 */
@Composable
fun PlanListScreen(
    onBack: () -> Unit,
    onPlanClick: (Long) -> Unit,
    viewModel: PlanListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlanListContent(
        uiState = uiState,
        onBack = onBack,
        onPlanClick = onPlanClick,
        onCreatePlan = viewModel::createPlan,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanListContent(
    uiState: PlanListUiState,
    onBack: () -> Unit,
    onPlanClick: (Long) -> Unit,
    onCreatePlan: (String, String, (Long) -> Unit) -> Unit,
) {
    var showNewPlanDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.plans_title)) },
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
            FloatingActionButton(onClick = { showNewPlanDialog = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.plans_add),
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding))

            uiState.plans.isEmpty() -> EmptyPlans(modifier = Modifier.padding(innerPadding))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(items = uiState.plans, key = { plan -> plan.id }) { plan ->
                    PlanRow(plan = plan, onClick = onPlanClick)
                }
            }
        }
    }

    if (showNewPlanDialog) {
        PlanFormDialog(
            title = stringResource(R.string.plans_add),
            confirmLabel = stringResource(R.string.action_add),
            onDismiss = { showNewPlanDialog = false },
            onConfirm = { name, description ->
                showNewPlanDialog = false
                onCreatePlan(name, description, onPlanClick)
            },
        )
    }
}

@Composable
private fun PlanRow(
    plan: Plan,
    onClick: (Long) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick(plan.id) },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            plan.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.author?.let { author ->
                    InfoTag(text = author)
                }
                if (plan.externalId != null) {
                    InfoTag(text = stringResource(R.string.plans_from_starter_data))
                }
            }
        }
    }
}

@Composable
private fun EmptyPlans(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.plans_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.plans_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
