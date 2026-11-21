package com.example.lab7

import com.example.lab7.data.local.ReadingDao
import com.example.lab7.data.local.ReadingEntity
import com.example.lab7.data.remote.ApiFactory
import com.example.lab7.data.repository.DefaultReadingRepository
import com.example.lab7.domain.model.Reading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingRepositoryTest {
    private class InMemoryDao : ReadingDao {
        val rows = MutableStateFlow<List<ReadingEntity>>(emptyList())
        private var nextId = 1L
        override suspend fun insert(reading: ReadingEntity): Long {
            if (rows.value.any { it.sampleId == reading.sampleId }) return -1
            rows.value = rows.value + reading.copy(id = nextId)
            return nextId++
        }
        override fun observeLatest(limit: Int): Flow<List<ReadingEntity>> = rows.map { it.reversed().take(limit) }
        override fun observeByKind(isEvent: Boolean, limit: Int) = rows.map { l -> l.filter { it.isEvent == isEvent }.reversed().take(limit) }
        override fun observePendingCount() = rows.map { l -> l.count { !it.synced } }
        override suspend fun pending(limit: Int) = rows.value.filter { !it.synced }.take(limit)
        override suspend fun markSynced(sampleIds: List<String>) {
            rows.value = rows.value.map { if (it.sampleId in sampleIds) it.copy(synced = true) else it }
        }
        override suspend fun trimSynced(keep: Int): Int = 0
    }

    private fun reading(id: String) = Reading(sampleId = id, timestamp = 1, rawLux = 400f, lux = 400f, lightClass = null, confidence = null)

    @Test fun recordIsSyncedOnlyAfterMatchingAcknowledgement() = runTest {
        MockWebServer().use { server ->
            server.start()
            val dao = InMemoryDao()
            val repo = DefaultReadingRepository(dao, ApiFactory.create(server.url("/").toString()) {}, "device-1")
            repo.save(reading("a"))
            repo.save(reading("b"))

            server.enqueue(MockResponse().setBody("{\"id\":1,\"sampleId\":\"a\"}"))
            server.enqueue(MockResponse().setBody("{\"id\":2,\"sampleId\":\"wrong\"}"))
            assertTrue(runCatching { repo.syncPending() }.isFailure)
            assertEquals(listOf(true, false), dao.rows.value.map { it.synced })

            val request = server.takeRequest().body.readUtf8()
            assertTrue("\"deviceId\":\"device-1\"" in request && "\"sampleId\":\"a\"" in request)

            server.enqueue(MockResponse().setResponseCode(503))
            assertTrue(runCatching { repo.syncPending() }.isFailure)
            server.enqueue(MockResponse().setBody("{\"id\":2,\"sampleId\":\"b\"}"))
            assertEquals(1, repo.syncPending())
            assertTrue(dao.rows.value.all { it.synced })
        }
    }

    @Test fun duplicateSampleIsNotStoredTwice() = runTest {
        val dao = InMemoryDao()
        val repo = DefaultReadingRepository(dao, ApiFactory.create("http://127.0.0.1:1/") {}, "d")
        repo.save(reading("same"))
        repo.save(reading("same"))
        assertEquals(1, dao.rows.value.size)
    }
}
