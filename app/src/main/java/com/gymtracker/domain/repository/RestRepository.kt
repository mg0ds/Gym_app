package com.gymtracker.domain.repository

import com.gymtracker.domain.model.RestEndReason
import com.gymtracker.domain.model.RestInterval
import com.gymtracker.domain.stats.RestStats
import kotlinx.coroutines.flow.Flow

/**
 * Przerwy między seriami.
 *
 * Repozytorium jest cienkie i celowo nie zna alarmów ani notyfikacji: to jego
 * zadanie kończy się na zapisaniu faktu („przerwa zaczęła się o 18:04:12
 * i skończyła po 94 sekundach, bo użytkownik wrócił do pracy”). Kto pilnuje
 * odliczania i budzi użytkownika, wie `RestTimer`.
 *
 * Zapis jest **natychmiastowy i idempotentny**: przerwę może domknąć alarm,
 * powrót do aplikacji albo kolejna seria - i nie ma znaczenia, kto zdąży
 * pierwszy.
 */
interface RestRepository {

    /**
     * Zapisuje start przerwy i zwraca jej wiersz (z faktycznym `started_at`).
     *
     * [isAuto] = przerwa ruszyła sama po zalogowanej serii (na razie zawsze tak;
     * kolumna czeka na ręczny start przerwy z karty ćwiczenia).
     */
    suspend fun start(
        exerciseId: Long,
        sessionId: Long?,
        setLogId: Long?,
        plannedSeconds: Int,
        isAuto: Boolean = true,
    ): RestInterval

    /** Korekta celu w trakcie odliczania („+15 s”, „-15 s”). */
    suspend fun setPlannedSeconds(intervalId: Long, plannedSeconds: Int)

    /**
     * Domyka przerwę: liczy faktyczny czas z zegara, zapisuje powód
     * i przepisuje przerwę do wiersza serii (o ile przerwa coś mówi o formie).
     *
     * Zwraca `false`, gdy przerwa była już domknięta - wtedy nic nie zmieniamy.
     */
    suspend fun close(intervalId: Long, reason: RestEndReason, plannedSeconds: Int): Boolean

    suspend fun get(intervalId: Long): RestInterval?

    /** Trwająca przerwa (o ile jest) - do odtworzenia odliczania po restarcie. */
    suspend fun getOpen(): RestInterval?

    /**
     * Statystyki przerw dla wskazanych ćwiczeń, kluczowane po `exerciseId`.
     * Ćwiczenie bez ani jednej przerwy nie trafia do mapy.
     */
    fun observeStats(exerciseIds: List<Long>): Flow<Map<Long, RestStats>>

    /** Wszystkie przerwy treningu (także pominięte) - podsumowanie na ekranie. */
    fun observeSessionRests(sessionId: Long): Flow<List<RestInterval>>

    /** Nazwa ćwiczenia - notyfikacja końca przerwy mówi, czego dotyczy. */
    suspend fun exerciseName(exerciseId: Long): String?
}
