package com.example.lab7

import com.example.lab7.domain.Actuator
import com.example.lab7.domain.HubLink
import com.example.lab7.domain.LightClassifier
import com.example.lab7.domain.NetworkMonitor
import com.example.lab7.domain.ReadingRepository
import com.example.lab7.domain.RemoteCommand
import com.example.lab7.domain.RemoteCommandSource
import com.example.lab7.domain.RuleRepository
import com.example.lab7.domain.SensorSource
import com.example.lab7.domain.model.LightClass
import com.example.lab7.domain.model.Prediction
import com.example.lab7.domain.model.Reading
import com.example.lab7.domain.model.SensorSnapshot
import com.example.lab7.domain.model.SmartRule
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSensors : SensorSource {
    val snapshot = MutableStateFlow(SensorSnapshot(lightAvailable = true, accelerometerAvailable = true))
    override fun readings(): Flow<SensorSnapshot> = snapshot
    fun lux(value: Float) { snapshot.value = snapshot.value.copy(lux = value) }
}

/** Замість TFLite: «різке затемнення», якщо нормалізоване значення впало більше ніж на 0,1 за вікно. */
class FakeClassifier : LightClassifier {
    override val windowSize = 20
    var closed = false
    override fun classify(window: FloatArray): Prediction {
        val shadow = window.first() - window.last() > 0.1f
        return if (shadow) Prediction(LightClass.SHADOW, listOf(0.02f, 0.03f, 0.95f))
        else Prediction(LightClass.STABLE, listOf(0.97f, 0.02f, 0.01f))
    }
    override fun close() { closed = true }
}

class FakeReadings : ReadingRepository {
    val stored = MutableStateFlow<List<Reading>>(emptyList())
    var failWrites = false
    var serverUp = true
    var uploads = 0

    override fun latest(limit: Int) = stored.map { it.takeLast(limit).reversed() }
    override fun measurements(limit: Int) = stored.map { list -> list.filter { !it.isEvent }.takeLast(limit) }
    override fun events(limit: Int) = stored.map { list -> list.filter { it.isEvent }.takeLast(limit) }
    override val pendingCount: Flow<Int> = stored.map { list -> list.count { !it.synced } }

    override suspend fun save(reading: Reading) {
        if (failWrites) throw IOException("database or disk is full")
        stored.value = stored.value + reading
    }

    override suspend fun syncPending(batch: Int): Int {
        if (!serverUp) throw IOException("hub is down")
        val pending = stored.value.filter { !it.synced }.take(batch)
        uploads += pending.size
        val ids = pending.map { it.sampleId }.toSet()
        stored.value = stored.value.map { if (it.sampleId in ids) it.copy(synced = true) else it }
        return pending.size
    }
}

class FakeRules(initial: List<SmartRule> = emptyList()) : RuleRepository {
    private val flow = MutableStateFlow(initial)
    override val rules: Flow<List<SmartRule>> = flow
    override suspend fun upsert(rule: SmartRule) { flow.value = flow.value.filter { it.id != rule.id } + rule }
    override suspend fun delete(id: Long) { flow.value = flow.value.filter { it.id != id } }
}

class FakeNetwork : NetworkMonitor {
    override val online = MutableStateFlow(true)
}

class FakeActuator : Actuator {
    var vibrations = 0
    var lamp = false
    override fun vibrate() { vibrations++ }
    override fun lamp(on: Boolean) { lamp = on }
    override fun release() { lamp = false }
}

class FakeHub : HubLink {
    override var connected = true
    val sent = mutableListOf<String>()
    override fun send(command: String) { sent += command }
}

class FakeRemote : RemoteCommandSource {
    var command = RemoteCommand(1, false)
    override suspend fun fetch() = command
}
