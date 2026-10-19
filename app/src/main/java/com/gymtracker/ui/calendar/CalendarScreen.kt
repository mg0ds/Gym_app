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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.DayWorkout
import com.gymtracker.domain.model.PlanDayOption
import com.gymtracker.domain.model.ScheduleStatus
import com.gymtracker.domain.model.ScheduledWorkout
import com.gymtracker.domain.model.SessionDetails
import com.gymtracker.ui.components.DatePickerRangeDialog
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * Kalendarz: miesiąc z zaznaczonymi treningami i lista wpisów wybranego dnia.
 *
 * Dzień, w którym coś się dzieje, dostaje **ramkę** (kolor mówi, co: odbyty,
 * zaplanowany, pominięty), a kropki w środku pokazują, ile rzeczy jest w dniu.
 * Znacznik dostaje też trening bez wpisu w planie („Nowy trening” z dnia bez
 * planu) - inaczej dzień wyglądałby na pusty, choć historia ma pod nim trening.
 * Z komórki wybieramy dzień, a z sekcji pod siatką - rozpoczynamy trening
 * (z listy dni planu albo od zera), odhaczamy dzień, przenosimy go, usuwamy
 * albo otwieramy trening, który nie był w planie.
 */
@Composable
fun CalendarScreen(
    onOpenPlans: () -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val blankWorkoutName = stringResource(R.string.session_workout_default_name)

    CalendarContent(
        uiState = uiState,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onToday = viewModel::showToday,
        onSelectDate = viewModel::selectDate,
        onStart = { entryId -> viewModel.startWorkout(entryId, onOpenSession) },
        onStartForDay = { date, planDayId ->
            viewModel.startForDay(date, planDayId, blankWorkoutName, onOpenSession)
        },
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
    onStartForDay: (LocalDate, Long?) -> Unit,
    onStatus: (Long, ScheduleStatus) -> Unit,
    onMove: (Long, LocalDate) -> Unit,
    onRemove: (Long) -> Unit,
    onOpenPlans: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    var entryToMove by remember { mutableStateOf<ScheduledWorkout?>(null) }
    var startForDate by remember { mutableStateOf<LocalDate?>(null) }
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
                    onStartForDay = { startForDate = uiState.selectedDate },
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
        // Przenosimy wpis tylko w oknie planowania (od dziś do horyzontu).
        DatePickerRangeDialog(
            initialDate = entry.date,
            minDate = uiState.today,
            maxDate = uiState.horizonEnd,
            onDismiss = { entryToMove = null },
            onConfirm = { date ->
                entryToMove = null
                onMove(entry.id, date)
            },
        )
    }

    startForDate?.let { date ->
        StartWorkoutSheet(
            date = date,
            locale = locale,
            options = uiState.dayOptions,
            onPick = { option ->
                startForDate = null
                onStartForDay(date, option.id)
            },
            onBlank = {
                startForDate = null
                onStartForDay(date, null)
            },
            onDismiss = { startForDate = null },
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
                    val entries = uiState.entriesByDate[cell.date].orEmpty()
                    DayCell(
                        cell = cell,
                        isToday = cell.date == uiState.today,
                        isSelected = cell.date == uiState.selectedDate,
                        entries = entries,
                        workouts = workoutsWithoutEntry(
                            entries = entries,
                            workouts = uiState.workoutsByDate[cell.date].orEmpty(),
                        ),
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
    workouts: List<DayWorkout>,
    onClick: () -> Unit,
) {
    val dayEntries = entries.takeIf { cell.inMonth }.orEmpty()
    val dayWorkouts = workouts.takeIf { cell.inMonth }.orEmpty()
    // Ramka wokół całego dnia mówi, że tego dnia coś jest - kropki zostały jako
    // „ile tego jest”. Wpisy i treningi wrzucamy do jednego worka, bo znaczą to
    // samo: trening bez wpisu też jest treningiem.
    val marks = dayEntries.map { entry -> entry.status } + dayWorkouts.map { workout -> workout.mark }
    val frameStatus = dayFrameStatus(marks)
    val frameColor = frameStatus?.let { status -> statusFrameColor(status) }
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }
    val digitColor = when {
        !cell.inMonth -> MaterialTheme.colorScheme.outline
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val statuses = marks.map { mark -> stringResource(statusLabelRes(mark)) }
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
            .clip(CELL_SHAPE)
            .background(background)
            .then(
                if (frameColor == null) {
                    Modifier
                } else {
                    Modifier.border(
                        width = if (isSelected) FRAME_WIDTH_SELECTED else FRAME_WIDTH,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else frameColor,
                        shape = CELL_SHAPE,
                    )
                },
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else null,
                color = digitColor,
            )
            StatusDots(statuses = marks)
        }
    }
}

/** Do trzech kropek na komórkę - resztę zwijamy w „+n”. */
@Composable
private fun StatusDots(statuses: List<ScheduleStatus>) {
    if (statuses.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        statuses.take(MAX_DOTS).forEach { status ->
            StatusDot(status = status)
        }
        if (statuses.size > MAX_DOTS) {
            Text(
                text = "+${statuses.size - MAX_DOTS}",
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

/**
 * Kolor ramki dnia dla jednego statusu. Wspólny dla siatki i legendy, żeby
 * legenda nie kłamała: „zaplanowany” w siatce jest stalowy, nie szary.
 */
@Composable
private fun statusFrameColor(status: ScheduleStatus): Color = when (status) {
    ScheduleStatus.DONE -> MaterialTheme.colorScheme.primary
    ScheduleStatus.PLANNED -> MaterialTheme.colorScheme.secondary
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
        // Legenda pokazuje to samo, co komórka dnia: ramkę z kropką w środku.
        Box(
            modifier = Modifier
                .size(LEGEND_SWATCH)
                .clip(RoundedCornerShape(5.dp))
                .border(FRAME_WIDTH, statusFrameColor(status), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            StatusDot(status = status)
        }
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
    onStartForDay: () -> Unit,
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

        // Rozpoczęcie treningu jest dostępne dla **każdego** dnia, nie tylko dla
        // pustego: wybrany dzień to dzień treningu, a nie dzień kliknięcia.
        Button(
            onClick = onStartForDay,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(BUTTON_ICON),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(R.string.calendar_start_workout))
        }

        if (uiState.selectedEntries.isEmpty() && uiState.selectedWorkouts.isEmpty()) {
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
            // Trening bez wpisu w planie („Nowy trening” z dnia bez planu) -
            // inaczej dzień twierdziłby, że nic się nie stało, a historia ma
            // pod nim trening.
            uiState.selectedWorkouts.forEach { workout ->
                WorkoutCard(workout = workout, onOpen = onOpenSession)
            }
        }
    }
}

/**
 * Trening dnia bez wpisu w planie. Karta pokazuje to, co kalendarz wie o tym
 * dniu: nazwę, status i to, że treningu nie było w planie.
 */
@Composable
private fun WorkoutCard(workout: DayWorkout, onOpen: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(workout.sessionId) },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = workout.name,
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
                    StatusDot(status = workout.mark)
                    Text(
                        text = stringResource(
                            if (workout.isFinished) {
                                R.string.schedule_status_done
                            } else {
                                R.string.calendar_workout_in_progress
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.calendar_workout_no_entry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Wybór treningu na konkretny dzień: dzień planu z listy albo trening od zera.
 *
 * Lista jest spłaszczona („plan → dzień” w jednym wierszu), bo użytkownik
 * wybiera trening na dzień, a nie plan - schodzenie do planu i z powrotem
 * byłoby tu zbędnym klikaniem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartWorkoutSheet(
    date: LocalDate,
    locale: Locale,
    options: List<PlanDayOption>,
    onPick: (PlanDayOption) -> Unit,
    onBlank: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.calendar_start_for, date.dayMonthText(locale)),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            )
            Text(
                text = stringResource(
                    R.string.calendar_start_date_hint,
                    date.weekdayText(locale),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )

            ListItem(
                modifier = Modifier.clickable(onClick = onBlank),
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                headlineContent = { Text(text = stringResource(R.string.calendar_start_blank)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.calendar_start_blank_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (options.isEmpty()) {
                Text(
                    text = stringResource(R.string.calendar_start_no_plans),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            } else {
                options.forEach { option ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(option) },
                        headlineContent = { Text(text = option.dayName) },
                        supportingContent = {
                            Text(
                                text = listOf(
                                    option.planName,
                                    pluralStringResource(
                                        R.plurals.calendar_start_exercises,
                                        option.exerciseCount,
                                        option.exerciseCount,
                                    ),
                                ).joinToString(separator = " • "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                }
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

/** Dni tygodnia od poniedziałku - kolejność siatki. */
private fun weekdaysFromMonday(): List<DayOfWeek> =
    (0 until DAYS_IN_WEEK).map { offset -> DayOfWeek.MONDAY.plus(offset.toLong()) }

private const val DAYS_IN_WEEK = 7
private const val MAX_DOTS = 3
private val CELL_HEIGHT = 52.dp
private val CELL_SHAPE = RoundedCornerShape(12.dp)
private val FRAME_WIDTH = 1.5.dp
private val FRAME_WIDTH_SELECTED = 2.dp
private val DOT_SIZE = 7.dp
private val LEGEND_SWATCH = 18.dp
private val BUTTON_ICON = 18.dp
