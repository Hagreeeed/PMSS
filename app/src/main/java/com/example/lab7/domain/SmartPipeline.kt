package com.example.lab7.domain

import com.example.lab7.domain.model.CloudStatus
import com.example.lab7.domain.model.ControlMode
import com.example.lab7.domain.model.LightClass
import com.example.lab7.domain.model.Prediction
import com.example.lab7.domain.model.Reading
import com.example.lab7.domain.model.RuleAction
import com.example.lab7.domain.model.SensorSnapshot
import com.example.lab7.domain.model.SmartRule
import com.example.lab7.domain.model.SyncPhase
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PipelineConfig(
    val tickMs: Long = 50,
    val predictEveryTicks: Int = 4,
    val recordMs: Long = 2_000,
    val fastRecordMs: Long = 500,
    val syncMs: Long = 5_000,
    val remotePollMs: Long = 3_000,
    /** Скільки лампа горить після різкого затемнення, яке побачила модель. */
    val shadowHoldMs: Long = 10_000,
    /** Скільки прогнозів поспіль модель має бачити затемнення, щоб система йому повірила. */
    val shadowVotes: Int = 3,
)

data class SystemState(
    val running: Boolean = false,
    val sensors: SensorSnapshot = SensorSnapshot(),
    val filteredLux: Float? = null,
    val windowFill: Int = 0,
    val windowSize: Int = 20,
    val prediction: Prediction? = null,
    val modelError: Boolean = false,
    val critical: Boolean = false,
    val mode: ControlMode = ControlMode.AUTO,
    val lampOn: Boolean = false,
    val lampReason: String = "Очікування датчика",
    val activeRules: List<SmartRule> = emptyList(),
    val recordIntervalMs: Long = 2_000,
    val cloud: CloudStatus = CloudStatus(),
    val remoteStatus: String = "—",
    val storageError: String? = null,
    val filteredSpikes: Int = 0,
    val log: List<String> = emptyList(),
)

/**
 * Наскрізний сценарій ЛР7:
 * датчик → медіанний фільтр → TFLite → рішення → Room → хмара, а для критичного стану
 * ще й актуатор і команда на Mock BLE-хаб. Усі зміни стану виконуються в [scope] (головний потік),
 * inference — у [computeDispatcher].
 */
class SmartPipeline(
    private val sensors: SensorSource,
    private val classifierFactory: () -> LightClassifier,
    private val readings: ReadingRepository,
    private val rules: RuleRepository,
    private val network: NetworkMonitor,
    private val actuator: Actuator,
    private val hub: HubLink,
    private val remote: RemoteCommandSource,
    private val scope: CoroutineScope,
    private val computeDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val logger: (String) -> Unit = {},
    private val config: PipelineConfig = PipelineConfig(),
) {
    private val mutableState = MutableStateFlow(SystemState(recordIntervalMs = config.recordMs))
    val state: StateFlow<SystemState> = mutableState.asStateFlow()

    private val filter = MedianFilter()
    private val decisionEngine = DecisionEngine()
    private val ruleEngine = RuleEngine()
    private val spikes = SpikeMonitor()
    private val syncMutex = Mutex()
    private var latest = SensorSnapshot()
    private var ruleList = emptyList<SmartRule>()
    private var shadowUntil = 0L
    private var shadowVotes = 0
    private var running: Job? = null
    private var remoteJob: Job? = null

    fun start() {
        if (running?.isActive == true) return
        mutableState.update { it.copy(running = true) }
        log("Моніторинг запущено")
        running = scope.launch {
            launch { sensors.readings().collect { snapshot -> latest = snapshot; mutableState.update { it.copy(sensors = snapshot) } } }
            launch { rules.rules.collect { ruleList = it } }
            launch { readings.pendingCount.collect { n -> updateCloud { it.copy(pending = n) } } }
            launch { network.online.collect(::onNetworkChanged) }
            launch { processLoop() }
            launch { recordLoop() }
            launch { syncLoop() }
        }
        restartRemote()
    }

    fun stop() {
        running?.cancel()
        remoteJob?.cancel()
        running = null
        remoteJob = null
        filter.reset()
        spikes.reset()
        decisionEngine.reset()
        ruleEngine.reset()
        shadowUntil = 0
        shadowVotes = 0
        latest = SensorSnapshot()
        actuator.release()
        mutableState.update {
            it.copy(running = false, lampOn = false, lampReason = "Застосунок у фоні: усе вимкнено",
                prediction = null, windowFill = 0, filteredLux = null, critical = false, activeRules = emptyList())
        }
        log("Моніторинг зупинено, актуатори вимкнено")
    }

    fun setMode(mode: ControlMode) {
        if (state.value.mode == mode) return
        mutableState.update { it.copy(mode = mode, remoteStatus = "—") }
        decisionEngine.reset()
        setLamp(false, "Режим $mode")
        log("Режим $mode")
        restartRemote()
    }

    fun manualLamp(on: Boolean) {
        if (state.value.mode == ControlMode.MANUAL) setLamp(on, "Ручне керування")
    }

    fun testVibration() {
        actuator.vibrate()
        log("Тест вібрації")
    }

    /** Негайна спроба синхронізації, наприклад після відновлення мережі. */
    fun syncNow() {
        scope.launch { sync() }
    }

    private suspend fun processLoop() {
        val classifier = try {
            withContext(computeDispatcher) { classifierFactory() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logger("Не вдалося завантажити модель: $error")
            mutableState.update { it.copy(modelError = true) }
            null
        }
        val window = LightWindow(classifier?.windowSize ?: 20)
        mutableState.update { it.copy(windowSize = window.size) }
        var ticks = 0
        try {
            while (currentCoroutineContext().isActive) {
                delay(config.tickMs)
                val raw = latest.lux
                val filtered = raw?.let(filter::add)
                if (filtered == null) {
                    filter.reset()
                    window.clear()
                } else {
                    window.add(filtered)
                    spikes.update(raw, filtered)?.let(::onSpike)
                }
                ticks++
                var prediction = state.value.prediction.takeIf { filtered != null }
                if (classifier != null && window.full && ticks % config.predictEveryTicks == 0) {
                    val input = window.snapshot()
                    prediction = withContext(computeDispatcher) { classifier.classify(input) }
                    // Затемнення підтверджується, лише якщо світло досі значно нижче за максимум у вікні.
                    val stillDark = filtered != null && filtered < window.maxLux() * 0.7f
                    shadowVotes = if (prediction.accepted == LightClass.SHADOW && stillDark) shadowVotes + 1 else 0
                }
                if (filtered == null) shadowVotes = 0
                step(filtered, prediction, window.count)
            }
        } finally {
            classifier?.let { withContext(NonCancellable + computeDispatcher) { it.close() } }
        }
    }

    private fun onSpike(spike: Spike) {
        mutableState.update { it.copy(filteredSpikes = it.filteredSpikes + 1) }
        log("Фільтр поглинув викид до ${spike.peakLux.toInt()} лк (~${spike.ticks * config.tickMs} мс)")
    }

    private fun step(filtered: Float?, prediction: Prediction?, windowFill: Int) {
        val now = clock()
        val accepted = prediction?.accepted
        if (shadowVotes >= config.shadowVotes) {
            if (now >= shadowUntil) log("AI: різке затемнення підтверджено (${(prediction!!.confidence * 100).toInt()} %)")
            shadowUntil = now + config.shadowHoldMs
        }
        val decision = decisionEngine.evaluate(filtered, now)
        val ruleResult = ruleEngine.evaluate(ruleList, filtered, latest)
        ruleResult.fired.forEach { runRule(it, filtered) }
        val ruleLamp = ruleResult.active.any { it.action == RuleAction.LAMP_ON }
        val shadow = now < shadowUntil
        val critical = filtered != null && (decision.lowLight || shadow)
        val interval = if (accepted == LightClass.CHANGING || accepted == LightClass.SHADOW) config.fastRecordMs else config.recordMs
        mutableState.update {
            it.copy(filteredLux = filtered, prediction = prediction, windowFill = windowFill, critical = critical,
                activeRules = ruleResult.active, recordIntervalMs = interval)
        }
        if (state.value.mode == ControlMode.AUTO) {
            val reason = when {
                shadow -> "AI: різке затемнення"
                decision.lowLight -> decision.reason
                ruleLamp -> "Правило: ${ruleResult.active.first { it.action == RuleAction.LAMP_ON }.title}"
                else -> decision.reason
            }
            setLamp(critical || ruleLamp, reason)
        }
    }

    private fun runRule(rule: SmartRule, lux: Float?) {
        log("Правило: ${rule.title}")
        when (rule.action) {
            RuleAction.VIBRATE -> actuator.vibrate()
            RuleAction.BLE_ALERT -> sendToHub("ALERT")
            RuleAction.LAMP_ON -> Unit // лампа тримається, поки умова правила виконується
        }
        recordEvent("Правило: ${rule.title}", lux)
    }

    private fun setLamp(on: Boolean, reason: String) {
        val current = state.value
        if (current.lampOn == on) {
            if (current.lampReason != reason) mutableState.update { it.copy(lampReason = reason) }
            return
        }
        mutableState.update { it.copy(lampOn = on, lampReason = reason) }
        actuator.lamp(on)
        if (on) actuator.vibrate()
        sendToHub(if (on) "LAMP:ON" else "LAMP:OFF")
        log("Лампа ${if (on) "ON" else "OFF"}: $reason")
        if (on) recordEvent("Лампа ON: $reason", state.value.filteredLux)
    }

    private fun sendToHub(command: String) {
        if (hub.connected) hub.send(command)
        else log("BLE-хаб не підключено, команду $command не надіслано")
    }

    private suspend fun recordLoop() {
        while (currentCoroutineContext().isActive) {
            delay(state.value.recordIntervalMs)
            val lux = state.value.filteredLux ?: continue
            save(newReading(lux, event = null))
        }
    }

    private fun recordEvent(event: String, lux: Float?) {
        val reading = newReading(lux, event)
        scope.launch { save(reading) }
    }

    private fun newReading(lux: Float?, event: String?): Reading {
        val prediction = state.value.prediction
        return Reading(
            sampleId = UUID.randomUUID().toString(),
            timestamp = clock(),
            rawLux = latest.lux,
            lux = lux,
            lightClass = prediction?.lightClass,
            confidence = prediction?.confidence,
            event = event,
        )
    }

    private suspend fun save(reading: Reading) {
        try {
            readings.save(reading)
            if (state.value.storageError != null) mutableState.update { it.copy(storageError = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Наприклад, SQLiteFullException: моніторинг і актуатори працюють далі без запису.
            mutableState.update { it.copy(storageError = "Не вдалося зберегти запис: ${error.javaClass.simpleName}") }
            logger("Помилка запису в Room: $error")
        }
    }

    private fun onNetworkChanged(online: Boolean) {
        val wasOnline = state.value.cloud.online
        updateCloud {
            it.copy(online = online,
                phase = if (online) SyncPhase.IDLE else SyncPhase.OFFLINE,
                message = if (online) "Мережа є" else "Немає мережі: записи чекають у Room")
        }
        if (online == wasOnline) return
        if (online) {
            log("Мережу відновлено, досилаємо записи")
            scope.launch { sync() }
        } else {
            log("Мережа зникла")
        }
    }

    private suspend fun syncLoop() {
        while (currentCoroutineContext().isActive) {
            delay(config.syncMs)
            sync()
        }
    }

    private suspend fun sync() = syncMutex.withLock {
        if (!state.value.cloud.online || state.value.cloud.pending == 0) return@withLock
        updateCloud { it.copy(phase = SyncPhase.SYNCING, message = "Відправка...") }
        try {
            var sent = 0
            do {
                val batch = readings.syncPending()
                sent += batch
            } while (batch > 0 && state.value.cloud.online && sent < 200)
            updateCloud { it.copy(phase = SyncPhase.SYNCED, lastSyncAt = clock(), message = "Відправлено: $sent") }
            if (sent > 0) logger("Синхронізовано записів: $sent")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            updateCloud { it.copy(phase = SyncPhase.ERROR, message = "Сервер недоступний, повтор через ${config.syncMs / 1000} с") }
            logger("Помилка синхронізації: $error")
        }
    }

    private fun restartRemote() {
        remoteJob?.cancel()
        if (running?.isActive != true || state.value.mode != ControlMode.REMOTE) return
        remoteJob = scope.launch {
            while (isActive) {
                try {
                    val command = remote.fetch()
                    mutableState.update { it.copy(remoteStatus = "Команда #${command.id}: ${if (command.lampOn) "ON" else "OFF"}") }
                    setLamp(command.lampOn, "Команда хаба #${command.id}")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    mutableState.update { it.copy(remoteStatus = "Хаб недоступний") }
                    setLamp(false, "Немає зв'язку з хабом: безпечний OFF")
                }
                delay(config.remotePollMs)
            }
        }
    }

    private fun updateCloud(change: (CloudStatus) -> CloudStatus) =
        mutableState.update { it.copy(cloud = change(it.cloud)) }

    private fun log(message: String) {
        logger(message)
        val time = java.time.LocalTime.now().withNano(0)
        mutableState.update { it.copy(log = (listOf("$time  $message") + it.log).take(30)) }
    }
}
