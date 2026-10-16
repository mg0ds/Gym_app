package com.gymtracker.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.ScheduleStatus
import com.gymtracker.domain.model.ScheduledWorkout
import com.gymtracker.domain.model.SessionDetails
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Kalendarz: miesiąc z zaznaczonymi treningami i lista wpisów wybranego dnia.
 *
 * Kropki w komórkach mówią, co się w danym dniu dzieje:
 * zielone/pełne = odbyty, obwódka = zaplanowany, blade = pominięty. Z komórki
 * wybieramy dzień, a z listy pod siatką - rozpoczynamy trening, odhaczamy dzień,
 * przenosimy go albo usuwamy z planu.
 */
@Composable
fun CalendarScreen(
    onOpenPlans: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CalendarContent(
        uiState = uiState,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onToday = viewModel::showToday,
        onSelectDate = viewModel::selectDate,
        onStart = { entryId -> viewModel.startWorkout(entryId, onOpenSession) },
        onStatus = viewModel::markStatus,
        onMove = viewModel::moveEntry,
        onRemove = viewModel::removeEntry,
        onOpenPlans = onOpenPlans,
        onOpenSession = onOpenSession,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarContent(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onStart: (Long) -> Unit,
    onStatus: (Long, ScheduleStatus) -> Unit,
    onMove: (Long, LocalDate) -> Unit,
    onRemove: (Long) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    var entryToMove by remember { mutableStateOf<ScheduledWorkout?>(null) }
    val locale = LocalConfiguration.current.locales[0]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.calendar_title)) },
                actions = {
                    TextButton(onClick = onToday) {
                        Text(text = stringResource(R.string.calendar_today))
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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            uiState.activeSession?.let { session ->
                ActiveSessionCard(session = session, onOpen = onOpenSession)
            }

            MonthHeader(
                uiState = uiState,
                locale = locale,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )

            WeekdayHeader(locale = locale)

            MonthGrid(
                uiState = uiState,
                onSelectDate = onSelectDate,
            )

            StatusLegend()

            // Dopóki wpisy się nie wczytają, nie mówimy „nic nie zaplanowano” -
            // to byłoby nieprawdą przez pierwszy ułamek sekundy po wejściu.
            if (!uiState.isLoading) {
                SelectedDaySection(
                    uiState = uiState,
                    locale = locale,
                    onStart = onStart,
                    onStatus = onStatus,
                    onMoveClick = { entry -> entryToMove = entry },
                    onRemove = onRemove,
                    onOpenPlans = onOpenPlans,
                    onOpenSession = onOpenSession,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    entryToMove?.let { entry ->
        MoveEntryDialog(
            entry = entry,
            minDate = uiState.today,
            maxDate = uiState.horizonEnd,
            onDismiss = { entryToMove = null },
            onConfirm = { date ->
                entryToMove = null
                onMove(entry.id, date)
            },
        )
    }
}

@Composable
private fun ActiveSessionCard(session: SessionDetails, onOpen: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.calendar_active_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = session.session.name, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { onOpen(session.session.id) }) {
                Text(text = stringResource(R.string.calendar_active_resume))
            }
        }
    }
}

@Composable
private fun MonthHeader(
    uiState: CalendarUiState,
    locale: Locale,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_previous_month),
            )
        }
        Text(
            text = uiState.month.headerText(locale),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNextMonth, enabled = uiState.canGoForward) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_next_month),
            )
        }
    }
}

@Composable
private fun WeekdayHeader(locale: Locale) {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdaysFromMonday().forEach { day ->
            Text(
                text = day.shortText(locale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    uiState: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        uiState.cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        isToday = cell.date == uiState.today,
                        isSelected = cell.date == uiState.selectedDate,
                        entries = uiState.entriesByDate[cell.date].orEmpty(),
                        onClick = { onSelectDate(cell.date) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    cell: CalendarCell,
    isToday: Boolean,
    isSelected: Boolean,
    entries: List<ScheduledWorkout>,
    onClick: () -> Unit,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val statuses = entries.map { entry -> stringResource(statusLabelRes(entry.status)) }
        .joinToString(separator = ", ")
    val description = if (statuses.isEmpty()) {
        cell.date.dayOfMonth.toString()
    } else {
        cell.date.dayOfMonth.toString() + ", " + statuses
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(CELL_HEIGHT)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (cell.inMonth) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
            StatusDots(entries = entries.takeIf { cell.inMonth }.orEmpty())
        }
    }
}

/** Do trzech kropek na komórkę - resztę zwijamy w „+n”. */
@Composable
private fun StatusDots(entries: List<ScheduledWorkout>) {
    if (entries.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        entries.take(MAX_DOTS).forEach { entry ->
            StatusDot(status = entry.status)
        }
        if (entries.size > MAX_DOTS) {
            Text(
                text = "+${entries.size - MAX_DOTS}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusDot(status: ScheduleStatus) {
    val planned = status == ScheduleStatus.PLANNED
    val color = statusDotColor(status)
    Box(
        modifier = Modifier
            .size(DOT_SIZE)
            .clip(CircleShape)
            .then(if (planned) Modifier.border(1.dp, color, CircleShape) else Modifier.background(color)),
    )
}

@Composable
private fun statusDotColor(status: ScheduleStatus): Color = when (status) {
    ScheduleStatus.DONE -> MaterialTheme.colorScheme.primary
    ScheduleStatus.PLANNED -> MaterialTheme.colorScheme.outline
    ScheduleStatus.SKIPPED -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun StatusLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LegendItem(ScheduleStatus.DONE)
        LegendItem(ScheduleStatus.PLANNED)
        LegendItem(ScheduleStatus.SKIPPED)
    }
}

@Composable
private fun LegendItem(status: ScheduleStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusDot(status = status)
        Text(
            text = stringResource(statusLabelRes(status)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectedDaySection(
    uiState: CalendarUiState,
    locale: Locale,
    onStart: (Long) -> Unit,
    onStatus: (Long, ScheduleStatus) -> Unit,
    onMoveClick: (ScheduledWorkout) -> Unit,
    onRemove: (Long) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val date = uiState.selectedDate
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.calendar_selected_day,
                    date.weekdayText(locale),
                    date.dayMonthText(locale),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (date == uiState.today) {
                Text(
                    text = stringResource(R.string.calendar_today),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        if (uiState.selectedEntries.isEmpty()) {
            Text(
                text = stringResource(R.string.calendar_day_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPlans) {
                Text(text = stringResource(R.string.calendar_plan_workout))
            }
        } else {
            uiState.selectedEntries.forEach { entry ->
                EntryCard(
                    entry = entry,
                    onStart = { onStart(entry.id) },
                    onStatus = { status -> onStatus(entry.id, status) },
                    onMoveClick = { onMoveClick(entry) },
                    onRemove = { onRemove(entry.id) },
                    onOpenSession = onOpenSession,
                )
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: ScheduledWorkout,
    onStart: () -> Unit,
    onStatus: (ScheduleStatus) -> Unit,
    onMoveClick: () -> Unit,
    onRemove: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        StatusDot(status = entry.status)
                        Text(
                            text = stringResource(statusLabelRes(entry.status)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (entry.isFromRecurrence) {
                        Text(
                            text = stringResource(R.string.schedule_from_recurrence),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (entry.isOpen) {
                FilledTonalIconButton(onClick = onStart) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.schedule_start),
                    )
                }
            }

            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.calendar_entry_actions),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (entry.isOpen) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.schedule_start)) },
                        onClick = {
                            menuOpen = false
                            onStart()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.schedule_mark_done)) },
                        onClick = {
                            menuOpen = false
                            onStatus(ScheduleStatus.DONE)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.schedule_mark_skipped)) },
                        onClick = {
                            menuOpen = false
                            onStatus(ScheduleStatus.SKIPPED)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.schedule_move)) },
                        onClick = {
                            menuOpen = false
                            onMoveClick()
                        },
                    )
                } else {
                    entry.sessionId?.let { sessionId ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.schedule_open_session)) },
                            onClick = {
                                menuOpen = false
                                onOpenSession(sessionId)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.schedule_restore)) },
                        onClick = {
                            menuOpen = false
                            onStatus(ScheduleStatus.PLANNED)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.schedule_remove)) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                )
            }
        }
    }
}

/** Wybór nowej daty dla wpisu - tylko w oknie planowania (od dziś do horyzontu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveEntryDialog(
    entry: ScheduledWorkout,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = entry.date.toUtcMillis(),
        selectableDates = DateRangeSelectableDates(minDate = minDate, maxDate = maxDate),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            val selected = state.selectedDateMillis
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { millis -> onConfirm(utcMillisToDate(millis)) } },
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/** Zakres dat dla `DatePicker` - material3 pyta o każdy dzień i każdy rok. */
@OptIn(ExperimentalMaterial3Api::class)
internal class DateRangeSelectableDates(
    private val minDate: LocalDate,
    private val maxDate: LocalDate,
) : SelectableDates {

    private val minMillis = minDate.toUtcMillis()
    private val maxMillis = maxDate.toUtcMillis()

    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis in minMillis..maxMillis

    override fun isSelectableYear(year: Int): Boolean = year in minDate.year..maxDate.year
}

/** Dni tygodnia od poniedziałku - kolejność siatki. */
private fun weekdaysFromMonday(): List<DayOfWeek> =
    (0 until DAYS_IN_WEEK).map { offset -> DayOfWeek.MONDAY.plus(offset.toLong()) }

private const val DAYS_IN_WEEK = 7
private const val MAX_DOTS = 3
private val CELL_HEIGHT = 52.dp
private val DOT_SIZE = 7.dp
