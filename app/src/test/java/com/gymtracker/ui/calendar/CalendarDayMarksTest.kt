package com.gymtracker.ui.calendar

import com.gymtracker.domain.model.DayWorkout
import com.gymtracker.domain.model.ScheduleStatus
import com.gymtracker.domain.model.ScheduledWorkout
import com.gymtracker.domain.model.SessionStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Znaczniki dnia: ramka i kropki.
 *
 * Kalendarz czyta dwa źródła - wpisy planu i treningi - więc sprawdzamy, że
 * trening bez wpisu też zaznacza dzień (M10: dzień z treningiem nie może
 * wyglądać na pusty), a trening powiązany z wpisem nie dubluje się na liście
 * dnia (wpis już go pokazuje).
 */
class CalendarDayMarksTest {

    private val day = LocalDate.of(2026, 10, 27)

    @Test
    fun `ramka wybiera najwazniejsze zdarzenie dnia`() {
        assertNull("pusty dzień nie ma ramki", dayFrameStatus(emptyList()))
        assertEquals(
            ScheduleStatus.SKIPPED,
            dayFrameStatus(listOf(ScheduleStatus.SKIPPED)),
        )
        assertEquals(
            ScheduleStatus.PLANNED,
            dayFrameStatus(listOf(ScheduleStatus.SKIPPED, ScheduleStatus.PLANNED)),
        )
        assertEquals(
            ScheduleStatus.DONE,
            dayFrameStatus(listOf(ScheduleStatus.PLANNED, ScheduleStatus.DONE, ScheduleStatus.SKIPPED)),
        )
    }

    @Test
    fun `trening bez wpisu zaznacza dzien jak odbyty`() {
        val workout = dayWorkout(id = 7L, status = SessionStatus.FINISHED)

        assertEquals(ScheduleStatus.DONE, workout.mark)
        assertEquals(
            listOf(workout),
            workoutsWithoutEntry(entries = emptyList(), workouts = listOf(workout)),
        )
    }

    @Test
    fun `trwajacy trening liczy sie jak zaplanowany`() {
        assertEquals(ScheduleStatus.PLANNED, dayWorkout(id = 7L, status = SessionStatus.ACTIVE).mark)
    }

    @Test
    fun `trening powiazany z wpisem nie dubluje sie na liscie dnia`() {
        val linked = dayWorkout(id = 7L, status = SessionStatus.FINISHED)
        val free = dayWorkout(id = 8L, status = SessionStatus.FINISHED)
        val entries = listOf(
            entry(id = 1L, status = ScheduleStatus.DONE, sessionId = linked.sessionId),
            entry(id = 2L, status = ScheduleStatus.PLANNED, sessionId = null),
        )

        assertEquals(
            "wpis odbyty pokazuje już swój trening",
            listOf(free),
            workoutsWithoutEntry(entries = entries, workouts = listOf(linked, free)),
        )
    }

    private fun dayWorkout(id: Long, status: SessionStatus) = DayWorkout(
        sessionId = id,
        date = day,
        name = "Trening",
        status = status,
    )

    private fun entry(id: Long, status: ScheduleStatus, sessionId: Long?) = ScheduledWorkout(
        id = id,
        date = day,
        title = "Dzień A",
        planId = 1L,
        planDayId = 1L,
        recurrenceId = null,
        status = status,
        sessionId = sessionId,
    )
}
