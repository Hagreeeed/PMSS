package com.example.lab7.domain

import com.example.lab7.domain.model.Prediction
import com.example.lab7.domain.model.Reading
import com.example.lab7.domain.model.SensorSnapshot
import com.example.lab7.domain.model.SmartRule
import kotlinx.coroutines.flow.Flow

// Інтерфейси, через які доменна логіка працює з Android і мережею.
// Реалізації лежать у пакеті data, у тестах їх підміняють фейками.

interface SensorSource {
    /** Холодний потік: слухач реєструється при збиранні й знімається при скасуванні. */
    fun readings(): Flow<SensorSnapshot>
}

interface LightClassifier : AutoCloseable {
    val windowSize: Int
    fun classify(window: FloatArray): Prediction
}

interface ReadingRepository {
    fun latest(limit: Int): Flow<List<Reading>>
    fun measurements(limit: Int): Flow<List<Reading>>
    fun events(limit: Int): Flow<List<Reading>>
    val pendingCount: Flow<Int>
    suspend fun save(reading: Reading)
    /** Відправляє невідправлені записи. Повертає кількість підтверджених сервером. */
    suspend fun syncPending(batch: Int = 20): Int
}

interface RuleRepository {
    val rules: Flow<List<SmartRule>>
    suspend fun upsert(rule: SmartRule)
    suspend fun delete(id: Long)
}

interface NetworkMonitor {
    val online: Flow<Boolean>
}

interface Actuator {
    fun vibrate()
    fun lamp(on: Boolean)
    fun release()
}

data class RemoteCommand(val id: Long, val lampOn: Boolean)

interface RemoteCommandSource {
    suspend fun fetch(): RemoteCommand
}

/** Канал до Mock BLE-хаба з ЛР2. */
interface HubLink {
    val connected: Boolean
    fun send(command: String)
}
