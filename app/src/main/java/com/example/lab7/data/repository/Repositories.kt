package com.example.lab7.data.repository

import com.example.lab7.data.local.ReadingDao
import com.example.lab7.data.local.RuleDao
import com.example.lab7.data.local.toDomain
import com.example.lab7.data.local.toDto
import com.example.lab7.data.local.toEntity
import com.example.lab7.data.remote.CloudApi
import com.example.lab7.domain.ReadingRepository
import com.example.lab7.domain.RuleRepository
import com.example.lab7.domain.model.Reading
import com.example.lab7.domain.model.SmartRule
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Єдине джерело історії: спершу запис у Room, потім відправка в хмару.
 * Запис позначається synced тільки після підтвердження сервером того самого sampleId.
 */
class DefaultReadingRepository(
    private val dao: ReadingDao,
    private val api: CloudApi,
    private val deviceId: String,
    private val keepRecords: Int = 5_000,
) : ReadingRepository {
    private var insertsSinceTrim = 0

    override fun latest(limit: Int): Flow<List<Reading>> =
        dao.observeLatest(limit).distinctUntilChanged().map { list -> list.map { it.toDomain() } }

    override fun measurements(limit: Int): Flow<List<Reading>> =
        dao.observeByKind(isEvent = false, limit).distinctUntilChanged().map { list -> list.map { it.toDomain() }.asReversed() }

    override fun events(limit: Int): Flow<List<Reading>> =
        dao.observeByKind(isEvent = true, limit).distinctUntilChanged().map { list -> list.map { it.toDomain() } }

    override val pendingCount: Flow<Int> = dao.observePendingCount().distinctUntilChanged()

    override suspend fun save(reading: Reading) {
        dao.insert(reading.toEntity())
        if (++insertsSinceTrim >= 100) {
            insertsSinceTrim = 0
            dao.trimSynced(keepRecords)
        }
    }

    override suspend fun syncPending(batch: Int): Int {
        val pending = dao.pending(batch)
        var confirmed = 0
        for (entity in pending) {
            val response = api.upload(entity.toDto(deviceId))
            val ack = response.body()
            if (!response.isSuccessful || ack?.sampleId != entity.sampleId) {
                throw IOException("HTTP ${response.code()} без підтвердження для ${entity.sampleId}")
            }
            dao.markSynced(listOf(entity.sampleId))
            confirmed++
        }
        return confirmed
    }
}

class DefaultRuleRepository(private val dao: RuleDao) : RuleRepository {
    override val rules: Flow<List<SmartRule>> = dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }
    override suspend fun upsert(rule: SmartRule) = dao.upsert(rule.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
}
