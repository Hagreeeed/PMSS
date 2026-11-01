package com.example.lab4

import com.example.lab4.cloud.*
import com.google.gson.JsonObject
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class CloudRepositoryTest {
    private class MemoryStore : PendingStore {
        var samples = emptyList<SensorData>()
        override suspend fun load() = samples
        override suspend fun save(samples: List<SensorData>) { this.samples = samples.toList() }
    }
    private class FakeApi : CloudApi {
        val received = mutableListOf<SensorData>()
        var failure = false
        var offline = false
        var waitForever = false
        var wrongAck = false
        override suspend fun postSensor(data: SensorData): Response<JsonObject> {
            received += data
            if (waitForever) awaitCancellation()
            if (offline) throw IOException("disconnected")
            if (failure) return Response.error(503, "Unavailable".toResponseBody("text/plain".toMediaType()))
            return Response.success(201, JsonObject().apply {
                addProperty("id", 101)
                addProperty("sampleId", if (wrongAck) "wrong-id" else data.sampleId)
            })
        }
    }
    private fun sample(value: Float = 420f) = SensorData("test-device", 1234L, value, x = 2f, y = -3f, z = 9.1f)

    @Test fun offlineSampleSurvivesRepositoryRecreation() = runTest {
        val store = MemoryStore(); val api = FakeApi(); val original = sample()
        val first = CloudRepository(api, store)
        first.cycle(original, false)
        assertEquals(SyncPhase.PENDING, first.state.value.phase)
        assertEquals(listOf(original), store.samples)
        assertTrue(api.received.isEmpty())
        val restored = CloudRepository(api, store)
        restored.restore()
        assertEquals(1, restored.state.value.pending)
        restored.cycle(null, true)
        assertEquals(listOf(original), api.received)
        assertTrue(store.samples.isEmpty())
        assertEquals(SyncPhase.SYNCED, restored.state.value.phase)
    }

    @Test fun httpErrorKeepsSameSampleUntilSuccessfulRetry() = runTest {
        val store = MemoryStore(); val api = FakeApi().apply { failure = true }
        val repository = CloudRepository(api, store); val original = sample()
        repository.cycle(original, true)
        assertEquals(503, repository.state.value.httpCode)
        assertEquals(listOf(original), store.samples)
        api.failure = false
        repository.cycle(null, true)
        assertEquals(listOf(original.sampleId, original.sampleId), api.received.map { it.sampleId })
        assertTrue(store.samples.isEmpty())
    }

    @Test fun cancellationRetainsDurablePendingSample() = runTest {
        val store = MemoryStore(); val api = FakeApi().apply { waitForever = true }
        val repository = CloudRepository(api, store); val original = sample()
        val job = launch { repository.cycle(original, true) }
        runCurrent(); job.cancel(); runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(listOf(original), store.samples)
        assertEquals(SyncPhase.PENDING, repository.state.value.phase)
    }

    @Test fun connectionFailureAndMismatchedAckDoNotLoseData() = runTest {
        val store = MemoryStore(); val api = FakeApi().apply { offline = true }
        val repository = CloudRepository(api, store); val original = sample()
        repository.cycle(original, true)
        assertEquals(1, repository.state.value.pending)
        api.offline = false; api.wrongAck = true
        repository.cycle(null, true)
        assertEquals(listOf(original), store.samples)
        assertEquals(SyncPhase.PENDING, repository.state.value.phase)
    }

    @Test fun backlogDrainsInFifoOrderWithMaximumThreePostsPerCycle() = runTest {
        val store = MemoryStore(); val api = FakeApi(); val repository = CloudRepository(api, store)
        val originals = (1..5).map { sample(it.toFloat()) }
        originals.forEach { repository.cycle(it, false) }
        repository.cycle(null, true)
        assertEquals(originals.take(3), api.received)
        assertEquals(originals.drop(3), store.samples)
        repository.cycle(null, true)
        assertEquals(originals, api.received)
        assertEquals(5, repository.state.value.sent)
    }

    @Test fun fullQueuePreservesPreviouslyAcceptedSamples() = runTest {
        val store = MemoryStore().apply { samples = List(120) { sample(it.toFloat()) } }
        val previous = store.samples
        val repository = CloudRepository(FakeApi(), store)
        repository.cycle(sample(999f), false)
        assertEquals(previous, store.samples)
        assertEquals(120, repository.state.value.pending)
    }

    @Test fun invalidNumbersAreNeverSerializedOrSent() = runTest {
        val store = MemoryStore(); val api = FakeApi(); val repository = CloudRepository(api, store)
        repository.cycle(sample(Float.NaN), true)
        repository.cycle(sample(-1f), true)
        assertTrue(api.received.isEmpty())
        assertTrue(store.samples.isEmpty())
    }

    @Test fun cancellationDuringPersistenceDoesNotDesynchronizeMemoryAndDisk() = runTest {
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val persisted = MemoryStore()
        val store = object : PendingStore {
            override suspend fun load() = persisted.load()
            override suspend fun save(samples: List<SensorData>) {
                if (!entered.isCompleted) { entered.complete(Unit); finish.await() }
                persisted.save(samples)
            }
        }
        val api = FakeApi(); val repository = CloudRepository(api, store); val original = sample()
        val job = launch { repository.cycle(original, false) }
        entered.await(); job.cancel(); finish.complete(Unit); runCurrent()
        assertEquals(listOf(original), persisted.samples)
        repository.cycle(null, true)
        assertEquals(listOf(original), api.received)
        assertTrue(persisted.samples.isEmpty())
    }
}
