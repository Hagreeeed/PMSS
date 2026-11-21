package com.example.lab4.control

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab4.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ControlViewModel(application: Application) : AndroidViewModel(application) {
    private val actuator: SmartActuator = SmartActuator(application) { controller.feedback(it) }
    private val controller: ActuatorController = ActuatorController(actuator) { Log.i("LightControl", it) }
    private val remote = RemoteRepository()
    val state = controller.state
    var endpoint = RemoteRepository.PUBLIC_URL
        private set
    private var polling: Job? = null
    fun onLux(value: Float?) = controller.onLux(value)
    fun manual(on: Boolean) = controller.manual(on)
    fun start() { actuator.start(); controller.start(); onLux(state.value.lux); restartPolling() }
    fun stop() { polling?.cancel(); polling = null; controller.stop() }
    fun select(mode: ControlMode) {
        polling?.cancel(); controller.select(mode); actuator.start(); restartPolling()
    }
    fun useDemo(enabled: Boolean) {
        if (enabled && !BuildConfig.DEBUG) return
        endpoint = if (enabled) RemoteRepository.DEMO_URL else RemoteRepository.PUBLIC_URL
        select(ControlMode.REMOTE)
    }
    fun hardware(enabled: Boolean) {
        controller.hardwareRequested(enabled)
        actuator.hardwareEnabled(enabled, state.value.lampOn)
    }
    private fun restartPolling() {
        polling?.cancel()
        if (!state.value.foreground || state.value.mode != ControlMode.REMOTE) return
        val url = endpoint
        polling = viewModelScope.launch {
            while (isActive) {
                try {
                    val (command, json) = remote.fetch(url)
                    Log.i("LightControl", "GET $url HTTP 200 $json")
                    controller.remote(command, json)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { controller.remoteError(error.message ?: error.javaClass.simpleName) }
                delay(3_000) // Ordinary polling, never long polling or instant push.
            }
        }
    }
    override fun onCleared() { stop(); super.onCleared() }
}
