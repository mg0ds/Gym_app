package com.gymtracker.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Źródło czasu dla warstwy danych. Wyciągnięte do interfejsu, żeby testy
 * mogły podstawić ustaloną datę zamiast `System.currentTimeMillis()`.
 */
fun interface TimeProvider {
    fun nowEpochMs(): Long
}

/**
 * „Dziś” wg zegara i strefy urządzenia. Strefę można podać w testach, ale
 * logika kalendarza i tak operuje na [LocalDate] - daty nie mają godziny, więc
 * zmiana czasu (DST) ich nie przesuwa.
 */
fun TimeProvider.today(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(nowEpochMs()).atZone(zone).toLocalDate()

/** Początek dnia jako epoch ms - odwrotność [today]. */
fun LocalDate.startOfDayEpochMs(zone: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
