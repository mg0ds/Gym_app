package com.gymtracker.domain.time

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Źródło czasu dla warstwy danych. Wyciągnięte do interfejsu, żeby testy
 * mogły podstawić ustaloną datę zamiast `System.currentTimeMillis()`.
 */
fun interface TimeProvider {
    fun nowEpochMs(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowEpochMs(): Long = System.currentTimeMillis()
}
