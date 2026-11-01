package com.example.lab4.control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ControlMode { AUTO, MANUAL, REMOTE }
data class HardwareFeedback(val torch: String = "Віртуальна лампа", val vibration: String = "Очікування")
data class ControlState(
    val mode: ControlMode = ControlMode.AUTO,
    val foreground: Boolean = false,
    val lux: Float? = null,
    val lampOn: Boolean = false,
    val reason: String = "Очікування сенсора",
    val remoteStatus: String = "Опитування зупинено",
    val lastJson: String = "—",
    val feedback: HardwareFeedback = HardwareFeedback(),
    val hardwareRequested: Boolean = false,
    val alarmCount: Int = 0,
    val logs: List<String> = emptyList(),
)

interface ActuatorPort {
    fun triggerAlarm()
    fun toggleFlashlight(status: Boolean)
    fun stop()
}

/** All commands are serialized on the main thread by the ViewModel. */
class ActuatorController(private val driver: ActuatorPort, private val logger: (String) -> Unit = {}) {
    private val mutable = MutableStateFlow(ControlState())
    val state = mutable.asStateFlow()
    private val engine = DecisionEngine()
    fun start() { mutable.value = state.value.copy(foreground = true) }
    fun stop() {
        mutable.value = state.value.copy(foreground = false, lampOn = false, reason = "onPause: OFF", remoteStatus = "Опитування зупинено")
        engine.reset()
        driver.stop()
        log("LIFECYCLE onPause: actuator OFF; polling cancelled")
    }
    fun select(mode: ControlMode) {
        engine.reset()
        driver.stop()
        mutable.value = state.value.copy(mode = mode, lampOn = false, reason = "Режим $mode: очікування команди", remoteStatus = "Очікування", lastJson = "—")
        log("MODE $mode: reset OFF")
        if (mode == ControlMode.AUTO) onLux(state.value.lux)
    }
    fun onLux(lux: Float?) {
        mutable.value = state.value.copy(lux = lux)
        if (state.value.foreground && state.value.mode == ControlMode.AUTO) {
            val decision = engine.evaluate(lux)
            apply(decision.lampOn, decision.reason)
        }
    }
    fun manual(on: Boolean) {
        if (state.value.mode == ControlMode.MANUAL) apply(on, "Manual Control: ${if (on) "ON" else "OFF"}")
    }
    fun remote(command: RemoteCommand, json: String) {
        if (!state.value.foreground || state.value.mode != ControlMode.REMOTE) return
        mutable.value = state.value.copy(remoteStatus = "HTTP 200 · command #${command.id}", lastJson = json)
        apply(command.on, "REST command #${command.id}: ${if (command.on) "ON" else "OFF"}")
    }
    fun remoteError(message: String) {
        if (!state.value.foreground || state.value.mode != ControlMode.REMOTE) return
        mutable.value = state.value.copy(remoteStatus = "Помилка: $message")
        apply(false, "Зв’язок втрачено: безпечний OFF")
        log("REMOTE ERROR $message; OFF; retry after 3 s")
    }
    fun feedback(value: HardwareFeedback) { mutable.value = state.value.copy(feedback = value) }
    fun hardwareRequested(value: Boolean) { mutable.value = state.value.copy(hardwareRequested = value) }
    private fun apply(on: Boolean, reason: String) {
        if (!state.value.foreground) return
        val changed = state.value.lampOn != on
        mutable.value = state.value.copy(lampOn = on, reason = reason)
        if (!changed) return // Do not vibrate on each sensor event or repeated poll.
        driver.toggleFlashlight(on)
        if (on) {
            mutable.value = state.value.copy(alarmCount = state.value.alarmCount + 1)
            driver.triggerAlarm()
        }
        log("ACTUATOR ${if (on) "ON" else "OFF"}: $reason")
    }
    private fun log(message: String) {
        logger(message)
        mutable.value = state.value.copy(logs = (listOf(message) + state.value.logs).take(25))
    }
}
