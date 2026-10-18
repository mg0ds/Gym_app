package com.gymtracker.data.repo

import androidx.room.withTransaction
import com.gymtracker.data.db.AppDatabase
import com.gymtracker.data.db.daos.BodyDao
import com.gymtracker.data.db.entities.BodyEntryEntity
import com.gymtracker.data.db.entities.MetricTypeEntity
import com.gymtracker.domain.model.BodyEntry
import com.gymtracker.domain.model.MetricDirection
import com.gymtracker.domain.model.MetricType
import com.gymtracker.domain.repository.BodyRepository
import com.gymtracker.domain.time.TimeProvider
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Karta postaci: wskaźniki i pomiary (M8).
 *
 * Zapis pomiaru idzie w transakcji, bo dotyka dwóch wierszy: pomiar z wybranego
 * dnia i (przy przenoszeniu daty) pomiar z dnia docelowego. Bez transakcji
 * przerwanie w połowie zostawiłoby dzień z dwoma pomiarami albo bez żadnego -
 * a baza i tak odrzuciłaby drugi wiersz kluczem `UNIQUE`.
 */
@Singleton
class BodyRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val bodyDao: BodyDao,
    private val timeProvider: TimeProvider,
) : BodyRepository {

    override fun observeTypes(includeArchived: Boolean): Flow<List<MetricType>> {
        val source = if (includeArchived) bodyDao.observeAllTypes() else bodyDao.observeActiveTypes()
        return source.map { rows -> rows.map { entity -> entity.toDomain() } }
    }

    override fun observeType(typeId: Long): Flow<MetricType?> =
        bodyDao.observeType(typeId).map { entity -> entity?.toDomain() }

    override fun observeEntries(metricTypeId: Long): Flow<List<BodyEntry>> =
        bodyDao.observeEntries(metricTypeId).map { rows -> rows.map { entity -> entity.toDomain() } }

    override suspend fun addType(
        name: String,
        unit: String?,
        direction: MetricDirection,
        target: Double?,
    ): Long? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null

        return bodyDao.insertType(
            MetricTypeEntity(
                name = cleanName,
                unit = cleanUnit(unit),
                direction = direction.wire,
                target = target,
                // Własne wskaźniki stoją za wbudowanymi (1..10) i za tym, co
                // użytkownik dodał wcześniej.
                position = bodyDao.maxTypePosition() + 1,
                createdAtEpochMs = timeProvider.nowEpochMs(),
            ),
        )
    }

    override suspend fun updateType(
        typeId: Long,
        name: String?,
        unit: String?,
        direction: MetricDirection,
        target: Double?,
    ) {
        val current = bodyDao.findType(typeId) ?: return
        val cleanName = name?.trim().orEmpty()

        // Puste pole znaczy „zostaw obecną nazwę” - dzięki temu ekran nie musi
        // wpisywać wbudowanemu wskaźnikowi nazwy z zasobów, a użytkownik może
        // zmienić sam cel, nie tracąc tłumaczonej nazwy.
        val newName = cleanName.ifEmpty { current.name }
        val builtinKey = if (cleanName.isEmpty() || cleanName == current.name) {
            current.builtinKey
        } else {
            // Własna nazwa jest od tej chwili decyzją użytkownika: tłumaczenie
            // nie ma prawa jej nadpisać.
            null
        }

        bodyDao.updateType(
            typeId = typeId,
            name = newName,
            unit = cleanUnit(unit),
            direction = direction.wire,
            target = target,
            builtinKey = builtinKey,
        )
    }

    override suspend fun setArchived(typeId: Long, archived: Boolean) {
        bodyDao.setArchived(typeId, archived)
    }

    override suspend fun saveEntry(
        metricTypeId: Long,
        entryId: Long?,
        date: LocalDate,
        value: Double,
        note: String?,
    ) {
        val day = date.toString()
        val cleanNote = note?.trim()?.takeIf { text -> text.isNotEmpty() }
        val nowEpochMs = timeProvider.nowEpochMs()

        database.withTransaction {
            if (entryId == null) {
                // Nowy pomiar. Gdy ten dzień już ma pomiar, poprawiamy go
                // zamiast dopisywać drugi - inaczej baza odrzuciłaby zapis
                // kluczem `UNIQUE`, a użytkownik zobaczyłby błąd bez wyjaśnienia.
                val existing = bodyDao.findEntry(metricTypeId, day)
                if (existing == null) {
                    bodyDao.insertEntry(
                        BodyEntryEntity(
                            metricTypeId = metricTypeId,
                            date = day,
                            measuredAtEpochMs = nowEpochMs,
                            value = value,
                            note = cleanNote,
                        ),
                    )
                } else {
                    bodyDao.updateEntry(
                        entryId = existing.id,
                        date = day,
                        measuredAtEpochMs = nowEpochMs,
                        value = value,
                        note = cleanNote,
                    )
                }
                return@withTransaction
            }

            // Poprawka istniejącego pomiaru: aktualizujemy **ten** wiersz, więc
            // zmiana daty przenosi pomiar, a nie tworzy drugiego. Dzień
            // docelowy zwalniamy z ewentualnego innego wiersza.
            bodyDao.deleteEntryOnDate(metricTypeId, day, keepEntryId = entryId)
            bodyDao.updateEntry(
                entryId = entryId,
                date = day,
                measuredAtEpochMs = nowEpochMs,
                value = value,
                note = cleanNote,
            )
        }
    }

    override suspend fun deleteEntry(entryId: Long) {
        bodyDao.deleteEntry(entryId)
    }

    /** „ ” i puste pole znaczą to samo: wskaźnik bez jednostki. */
    private fun cleanUnit(unit: String?): String? = unit?.trim()?.takeIf { text -> text.isNotEmpty() }
}
