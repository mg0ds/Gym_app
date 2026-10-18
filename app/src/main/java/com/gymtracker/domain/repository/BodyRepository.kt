package com.gymtracker.domain.repository

import com.gymtracker.domain.model.BodyEntry
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.model.MetricType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Karta postaci: wskaźniki (waga, obwody, własne) i ich pomiary (M8).
 *
 * Repozytorium nie wie nic o wykresach - oddaje wskaźniki i pomiary, a reguły
 * (zakres, zmiany, seria) liczy `BodyProgressCalculator`. Dzięki temu zmiana
 * progu „ostatnie 30 dni” to zmiana w domenie z testem bez bazy.
 */
interface BodyRepository {

    /** Aktywne wskaźniki; [includeArchived] dołącza zarchiwizowane (ekran „Wskaźniki”). */
    fun observeTypes(includeArchived: Boolean = false): Flow<List<MetricType>>

    fun observeType(typeId: Long): Flow<MetricType?>

    /** Pomiary jednego wskaźnika od najnowszych. */
    fun observeEntries(metricTypeId: Long): Flow<List<BodyEntry>>

    /**
     * Nowy wskaźnik. Zwraca jego identyfikator.
     *
     * Pusta (albo sama spacja) nazwa nic nie zapisuje: wskaźnik bez nazwy nie
     * miałby jak wyglądać na liście. Ekran blokuje taki zapis, a repozytorium
     * nie tworzy wiersza-kaleki, gdyby gdzieś go przepuścił.
     */
    suspend fun addType(
        name: String,
        unit: String?,
        direction: MetricDirection,
        target: Double?,
    ): Long?

    /**
     * Zmiana ustawień wskaźnika.
     *
     * Puste [name] znaczy „zostaw obecną nazwę”: wbudowanemu wskaźnikowi nie
     * trzeba wpisywać nazwy z zasobów, żeby zmienić sam cel, a jego tłumaczona
     * nazwa przetrwa. Wpisanie nowej nazwy odbiera mu klucz wbudowanego
     * wskaźnika - od tej chwili wygrywa nazwa użytkownika.
     */
    suspend fun updateType(
        typeId: Long,
        name: String?,
        unit: String?,
        direction: MetricDirection,
        target: Double?,
    )

    /** Archiwizacja/odarchiwizowanie; pomiary zostają nietknięte. */
    suspend fun setArchived(typeId: Long, archived: Boolean)

    /**
     * Zapis pomiaru z danego dnia.
     *
     * [entryId] == `null` to nowy pomiar (gdy dzień już ma pomiar, tamten jest
     * poprawiany - jeden pomiar na dzień), a podany identyfikator to poprawka
     * istniejącego pomiaru: wtedy zmiana daty **przenosi** pomiar, a nie
     * zostawia po nim sierotę w starym dniu.
     */
    suspend fun saveEntry(
        metricTypeId: Long,
        entryId: Long?,
        date: LocalDate,
        value: Double,
        note: String?,
    )

    suspend fun deleteEntry(entryId: Long)
}
