package com.gymtracker.domain.model

import java.time.LocalDate

/**
 * W którą stronę zmiana wskaźnika jest **dobra dla użytkownika**.
 *
 * Aplikacja nie ocenia, co jest zdrowe: masa ciała bywa celem w obie strony
 * (redukcja i masa), więc kierunek ustawia użytkownik. Dlatego wbudowane
 * wskaźniki startują jako `neutral` i nic nie jest domyślnie „na plus” -
 * kolory zmian pojawiają się dopiero, gdy użytkownik wskaże swoją stronę.
 */
enum class MetricDirection(val wire: String) {
    UP_GOOD("up_good"),
    DOWN_GOOD("down_good"),
    NEUTRAL("neutral");

    /**
     * Czy zmiana [change] idzie w dobrą stronę. `null` przy kierunku
     * `neutral` i przy braku zmiany: nie mamy wtedy nic do powiedzenia
     * i nie udajemy oceny.
     */
    fun isImprovement(change: Double?): Boolean? = when {
        change == null || change == 0.0 -> null
        this == UP_GOOD -> change > 0.0
        this == DOWN_GOOD -> change < 0.0
        else -> null
    }

    companion object {
        /** Nieznana wartość z bazy (starszy wiersz) traktowana jest jak brak preferencji. */
        fun fromWire(wire: String?): MetricDirection =
            entries.firstOrNull { direction -> direction.wire == wire } ?: NEUTRAL
    }
}

/**
 * Wbudowane wskaźniki karty postaci.
 *
 * Trzymamy je w jednym miejscu, bo widzą je dwie warstwy: migracja (zakłada je
 * w bazie) i UI (tłumaczy nazwy). Dopóki wskaźnik ma swój [wire], ekran pokazuje
 * nazwę z zasobów, więc polski i angielski interfejs widzą swoje nazwy; po
 * pierwszej zmianie nazwy przez użytkownika wygrywa wpis z bazy.
 *
 * [defaultName] nigdy nie trafia na ekran, dopóki [wire] jest ustawiony -
 * jest tylko czytelnym zapasem dla wiersza w bazie.
 */
enum class BuiltinMetric(val wire: String, val defaultName: String, val unit: String) {
    WEIGHT("weight", "Weight", "kg"),
    CHEST("chest", "Chest", "cm"),
    BICEPS("biceps", "Biceps", "cm"),
    WAIST("waist", "Waist", "cm"),
    THIGH("thigh", "Thigh", "cm"),
    CALF("calf", "Calf", "cm"),
    BODY_FAT("body_fat", "Body fat", "%"),
    NECK("neck", "Neck", "cm"),
    SHOULDERS("shoulders", "Shoulders", "cm"),
    FOREARM("forearm", "Forearm", "cm");

    companion object {
        fun fromWire(wire: String?): BuiltinMetric? =
            entries.firstOrNull { metric -> metric.wire == wire }
    }
}

/**
 * Wskaźnik karty postaci: nazwa, jednostka, kierunek i opcjonalny cel.
 *
 * [builtin] != null znaczy „to wbudowany wskaźnik i nikt nie zmienił nazwy” -
 * wtedy ekran bierze nazwę z zasobów. Zmiana nazwy czyści [builtin] (patrz
 * `BodyDao.updateType`), więc własna nazwa nie zostanie nadpisana tłumaczeniem.
 */
data class MetricType(
    val id: Long,
    val name: String,
    val unit: String? = null,
    val direction: MetricDirection = MetricDirection.NEUTRAL,
    val target: Double? = null,
    val builtin: BuiltinMetric? = null,
    val isArchived: Boolean = false,
    val position: Int = 0,
)

/** Pojedynczy pomiar: dzień, wartość i opcjonalna notatka. */
data class BodyEntry(
    val id: Long,
    val metricTypeId: Long,
    val date: LocalDate,
    val measuredAtEpochMs: Long,
    val value: Double,
    val note: String? = null,
)
