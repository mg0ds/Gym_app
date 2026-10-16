package com.gymtracker.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.R
import com.gymtracker.domain.model.PlanDay
import com.gymtracker.domain.schedule.ScheduleError
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Formularz harmonogramu planu: reguła powtarzania albo ręcznie wybrane dni.
 *
 * Podgląd jest tu najważniejszy - pokazuje dokładnie te daty, które trafią do
 * kalendarza, razem z przypisanym dniem planu (rotacja A/B/C). Dzięki temu
 * „co 3 dni, od 1.10 do 31.12, rotacja” widać jeszcze przed zapisem.
 *
 * Daty wybieramy tylko z okna planowania (od dziś do 20 tygodni w przód) -
 * zawęża je sam kalendarz, więc nie da się zaplanować czegoś poza horyzontem.
 */
@Composable
fun ScheduleFormScreen(
    onBack: () -> Unit,
    viewModel: ScheduleFormViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScheduleFormContent(
        uiState = uiState,
        onBack = onBack,
        onModeChange = viewModel::setMode,
        onSelectDay = viewModel::selectDay,
        onStartDateChange = viewModel::setStartDate,
        onEndDateChange = viewModel::setEndDate,
        onClearEndDate = viewModel::clearEndDate,
        onIntervalChange = viewModel::setInterval,
        onToggleWeekday = viewModel::toggleWeekday,
        onClearWeekdays = viewModel::clearWeekdays,
        onUsePlanWeekdays = viewModel::applyWeekdaysFromPlan,
        onToggleDate = viewModel::togglePickedDate,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onSave = { viewModel.save(onSaved = onBack) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleFormContent(
    uiState: ScheduleFormUiState,
    onBack: () -> Unit,
    onModeChange: (ScheduleMode) -> Unit,
    onSelectDay: (Long?) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onClearEndDate: () -> Unit,
    onIntervalChange: (String) -> Unit,
    onToggleWeekday: (DayOfWeek) -> Unit,
    onClearWeekdays: () -> Unit,
    onUsePlanWeekdays: () -> Unit,
    onToggleDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSave: () -> Unit,
) {
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEditing) {
                                R.string.schedule_form_title_edit
                            } else {
                                R.string.schedule_form_title
                            },
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
                    TextButton(onClick = onSave, enabled = uiState.canSave) {
                        Text(text = stringResource(R.string.action_save))
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.planName.isNotEmpty()) {
                Text(
                    text = uiState.planName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ModeSection(mode = uiState.mode, onModeChange = onModeChange)

            DaySection(uiState = uiState, onSelectDay = onSelectDay)

            if (uiState.isRule) {
                RuleSection(
                    uiState = uiState,
                    locale = locale,
                    onPickStart = { pickStart = true },
                    onPickEnd = { pickEnd = true },
                    onClearEndDate = onClearEndDate,
                    onIntervalChange = onIntervalChange,
                    onToggleWeekday = onToggleWeekday,
                    onClearWeekdays = onClearWeekdays,
                    onUsePlanWeekdays = onUsePlanWeekdays,
                )
            } else {
                PickedDatesSection(
                    uiState = uiState,
                    locale = locale,
                    onToggleDate = onToggleDate,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                )
            }

            PreviewSection(uiState = uiState, locale = locale)

            ErrorSection(uiState = uiState)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (pickStart) {
        PickDateDialog(
            title = stringResource(R.string.schedule_field_start),
            initialDate = uiState.startDate,
            minDate = uiState.today,
            maxDate = uiState.horizonEnd,
            onDismiss = { pickStart = false },
            onConfirm = { date ->
                pickStart = false
                onStartDateChange(date)
            },
        )
    }

    if (pickEnd) {
        PickDateDialog(
            title = stringResource(R.string.schedule_field_end),
            initialDate = uiState.endDate ?: uiState.startDate,
            minDate = uiState.startDate,
            maxDate = uiState.horizonEnd,
            onDismiss = { pickEnd = false },
            onConfirm = { date ->
                pickEnd = false
                onEndDateChange(date)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSection(mode: ScheduleMode, onModeChange: (ScheduleMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = mode == ScheduleMode.RULE,
            onClick = { onModeChange(ScheduleMode.RULE) },
            label = { Text(text = stringResource(R.string.schedule_mode_rule)) },
        )
        FilterChip(
            selected = mode == ScheduleMode.DATES,
            onClick = { onModeChange(ScheduleMode.DATES) },
            label = { Text(text = stringResource(R.string.schedule_mode_dates)) },
        )
    }
}

/** Wybór dnia: rotacja po dniach planu albo jeden konkretny dzień. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySection(uiState: ScheduleFormUiState, onSelectDay: (Long?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.schedule_field_days),
            style = MaterialTheme.typography.titleSmall,
        )
        ChipRow {
            FilterChip(
                selected = uiState.rotatesDays,
                enabled = uiState.planDays.size > 1,
                onClick = { onSelectDay(null) },
                label = { Text(text = stringResource(R.string.schedule_rotate_option)) },
            )
            uiState.planDays.forEach { day ->
                FilterChip(
                    selected = uiState.selectedDayId == day.id,
                    onClick = { onSelectDay(day.id) },
                    label = { Text(text = day.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        if (uiState.rotatesDays) {
            Text(
                text = stringResource(R.string.schedule_rotate_hint, rotationLabel(uiState.planDays)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RuleSection(
    uiState: ScheduleFormUiState,
    locale: Locale,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onClearEndDate: () -> Unit,
    onIntervalChange: (String) -> Unit,
    onToggleWeekday: (DayOfWeek) -> Unit,
    onClearWeekdays: () -> Unit,
    onUsePlanWeekdays: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(
            label = stringResource(R.string.schedule_field_start),
            value = uiState.startDate.shortDayText(locale),
            onClick = onPickStart,
        )

        DateField(
            label = stringResource(R.string.schedule_field_end_optional),
            value = uiState.endDate?.shortDayText(locale),
            onClick = onPickEnd,
            onClear = if (uiState.endDate != null) onClearEndDate else null,
        )

        OutlinedTextField(
            value = uiState.intervalText,
            onValueChange = onIntervalChange,
            label = { Text(text = stringResource(R.string.schedule_field_interval)) },
            singleLine = true,
            isError = uiState.intervalInvalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.schedule_field_weekdays),
                style = MaterialTheme.typography.titleSmall,
            )
            ChipRow {
                (0 until DAYS_IN_WEEK).map { offset -> DayOfWeek.MONDAY.plus(offset.toLong()) }
                    .forEach { day ->
                        FilterChip(
                            selected = day in uiState.weekdays,
                            onClick = { onToggleWeekday(day) },
                            label = { Text(text = day.shortText(locale)) },
                        )
                    }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.weekdaysFromPlan.isNotEmpty()) {
                    TextButton(onClick = onUsePlanWeekdays) {
                        Text(
                            text = stringResource(
                                R.string.schedule_weekdays_from_plan,
                                uiState.weekdaysFromPlan.sorted()
                                    .joinToString(separator = ", ") { day -> day.shortText(locale) },
                            ),
                        )
                    }
                }
                if (uiState.weekdays.isNotEmpty()) {
                    TextButton(onClick = onClearWeekdays) {
                        Text(text = stringResource(R.string.schedule_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun PickedDatesSection(
    uiState: ScheduleFormUiState,
    locale: Locale,
    onToggleDate: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val canGoForward = uiState.month.isBefore(YearMonth.from(uiState.horizonEnd))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.schedule_pick_dates_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pluralStringResource(
                R.plurals.schedule_selected_days,
                uiState.pickedDates.size,
                uiState.pickedDates.size,
            ),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.calendar_previous_month),
                )
            }
            Text(
                text = uiState.month.headerText(locale),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNextMonth, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.calendar_next_month),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            (0 until DAYS_IN_WEEK).map { offset -> DayOfWeek.MONDAY.plus(offset.toLong()) }
                .forEach { day ->
                    Text(
                        text = day.shortText(locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            monthGrid(uiState.month).chunked(DAYS_IN_WEEK).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        PickDayCell(
                            date = cell.date,
                            inMonth = cell.inMonth,
                            selected = cell.date in uiState.pickedDates,
                            inWindow = !cell.date.isBefore(uiState.today) &&
                                !cell.date.isAfter(uiState.horizonEnd),
                            onClick = { onToggleDate(cell.date) },
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.schedule_horizon_hint, uiState.horizonEnd.isoText()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowScope.PickDayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    inWindow: Boolean,
    onClick: () -> Unit,
) {
    val selectable = inWindow
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = when {
        !selectable -> MaterialTheme.colorScheme.outlineVariant
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(PICK_CELL_HEIGHT)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(
                if (selectable) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
        )
    }
}

@Composable
private fun PreviewSection(uiState: ScheduleFormUiState, locale: Locale) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.schedule_preview_title),
                style = MaterialTheme.typography.titleSmall,
            )

            if (uiState.preview.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_preview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = pluralStringResource(
                        R.plurals.schedule_preview_count,
                        uiState.preview.size,
                        uiState.preview.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                uiState.preview.take(PREVIEW_LIMIT).forEach { workout ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = workout.date.shortDayText(locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = workout.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                val hidden = uiState.preview.size - PREVIEW_LIMIT
                if (hidden > 0) {
                    Text(
                        text = stringResource(R.string.schedule_preview_more, hidden),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorSection(uiState: ScheduleFormUiState) {
    val messages = buildList {
        if (uiState.isRule && uiState.intervalInvalid) {
            // Pusta albo nieliczbowa wartość - jedna podpowiedź zamiast dwóch
            // (domena i tak zgłosi INTERVAL_TOO_SMALL, bo 0 jest mniejsze od 1).
            add(stringResource(R.string.schedule_error_interval_invalid))
        } else {
            uiState.errors.forEach { error -> add(stringResource(errorMessageRes(error))) }
        }
    }
    messages.forEach { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Data wybrana z kalendarza: etykieta, wartość i opcjonalne czyszczenie. */
@Composable
private fun DateField(
    label: String,
    value: String?,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value ?: stringResource(R.string.schedule_field_end_none),
                style = MaterialTheme.typography.bodyLarge,
                color = if (value == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            if (onClear != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.schedule_clear),
                    )
                }
            }
        }
    }
}

/**
 * Chipy w jednym rzędzie, przewijane w poziomie. Dni tygodnia jest siedem,
 * a dni planu bywa więcej - na wąskim ekranie nie mieszczą się w całości, więc
 * rząd się przewija zamiast ucinać ostatnią opcję.
 */
@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDateDialog(
    title: String,
    initialDate: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcMillis(),
        selectableDates = DateRangeSelectableDates(minDate = minDate, maxDate = maxDate),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(utcMillisToDate(requireNotNull(state.selectedDateMillis))) },
                enabled = state.selectedDateMillis != null,
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
        DatePicker(state = state, title = { Text(text = title, modifier = Modifier.padding(12.dp)) })
    }
}

@StringRes
private fun errorMessageRes(error: ScheduleError): Int = when (error) {
    ScheduleError.START_BEYOND_HORIZON -> R.string.schedule_error_start_beyond_horizon
    ScheduleError.END_BEFORE_START -> R.string.schedule_error_end_before_start
    ScheduleError.END_BEYOND_HORIZON -> R.string.schedule_error_end_beyond_horizon
    ScheduleError.INTERVAL_TOO_SMALL -> R.string.schedule_error_interval_too_small
    ScheduleError.NO_PLAN_DAY -> R.string.schedule_error_no_plan_day
}

/** „A → B → C” - podpowiedź, jak rotacja przełoży się na dni. */
private fun rotationLabel(days: List<PlanDay>): String =
    days.joinToString(separator = " → ") { day -> day.name }

private const val DAYS_IN_WEEK = 7
private const val PREVIEW_LIMIT = 6
private val PICK_CELL_HEIGHT = 40.dp
