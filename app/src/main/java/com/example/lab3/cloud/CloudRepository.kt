package com.example.lab3.cloud

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface PendingStore {
    suspend fun load(): List<SensorData>
    suspend fun save(samples: List<SensorData>)
}

/** Durable FIFO outbox. Only a successful, matching API acknowledgement removes a sample. */
class CloudRepository(
    private val api: CloudApi,
    private val store: PendingStore,
    private val logger: (String) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(CloudState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var loaded = false
    private var queue = emptyList<SensorData>()

    suspend fun restore() = mutex.withLock { restoreLocked() }

    private suspend fun restoreLocked() {
        if (loaded) return
        queue = store.load()
        loaded = true
        mutableState.value = state.value.copy(
            pending = queue.size,
            phase = if (queue.isEmpty()) SyncPhase.WAITING else SyncPhase.PENDING,
            message = if (queue.isEmpty()) "Очікування першого вимірювання" else "Відновлено Pending: ${queue.size}",
        )
    }

    /** Called once per 5-second foreground cycle. Drain at most 3 records, sequentially. */
    suspend fun cycle(sample: SensorData?, online: Boolean) = mutex.withLock {
        try {
            restoreLocked()
            if (sample != null && valid(sample)) {
                if (queue.size < MAX_PENDING) replaceQueue(queue + sample)
                else log("Черга повна: новий вимір пропущено; збережені записи залишено")
            }
            if (queue.isEmpty()) return@withLock
            if (!online) {
                pending("Немає інтернету. Дані збережено: Pending")
                return@withLock
            }
            repeat(minOf(queue.size, 3)) {
                val entry = queue.first()
                val json = CloudClient.gson.toJson(entry)
                mutableState.value = state.value.copy(phase = SyncPhase.SENDING, lastRequest = json)
                log("POST /posts sample=${entry.sampleId} lux=${entry.value} xyz=${entry.x},${entry.y},${entry.z}")
                val response = api.postSensor(entry)
                val body = response.body()
                mutableState.value = state.value.copy(
                    httpCode = response.code(),
                    lastResponse = body?.let { CloudClient.gson.toJson(it) }
                        ?: response.errorBody()?.use { it.string().take(2_000) }.orEmpty(),
                )
                if (!response.isSuccessful || body == null || body.get("sampleId")?.asString != entry.sampleId || !body.has("id")) {
                    pending("HTTP ${response.code()}: немає коректного підтвердження. Pending")
                    return@withLock
                }
                // Persist removal only after ACK; a cancelled/failed POST retains its original sampleId.
                replaceQueue(queue.drop(1))
                mutableState.value = state.value.copy(sent = state.value.sent + 1)
                log("HTTP ${response.code()} ACK sample=${entry.sampleId} id=${body.get("id")}")
            }
            mutableState.value = state.value.copy(
                phase = if (queue.isEmpty()) SyncPhase.SYNCED else SyncPhase.PENDING,
                message = if (queue.isEmpty()) "Успішно · API прийняв дані (demo)" else "Черга Pending: ${queue.size}",
            )
        } catch (cancelled: CancellationException) {
            if (queue.isNotEmpty()) pending("Передачу призупинено. Pending")
            throw cancelled
        } catch (error: IOException) {
            pending("Помилка мережі або локального сховища. Pending")
            log(error.javaClass.simpleName + ": " + error.message.orEmpty().take(180))
        } catch (error: RuntimeException) {
            pending("Некоректна відповідь API. Pending")
            log(error.javaClass.simpleName)
        }
    }

    private suspend fun replaceQueue(next: List<SensorData>) = withContext(NonCancellable) {
        // Keep disk and memory consistent even when onPause cancels during an IO commit.
        store.save(next)
        queue = next
        mutableState.value = state.value.copy(pending = queue.size)
    }

    private fun pending(message: String) {
        mutableState.value = state.value.copy(phase = SyncPhase.PENDING, pending = queue.size, message = message)
        log(message)
    }

    private fun log(message: String) {
        mutableState.value = state.value.copy(logs = (state.value.logs + message).takeLast(40))
        logger(message)
    }

    private fun valid(sample: SensorData): Boolean = sample.value.isFinite() && sample.value >= 0 &&
        listOfNotNull(sample.x, sample.y, sample.z).all { it.isFinite() }

    companion object { const val MAX_PENDING = 120 }
}
