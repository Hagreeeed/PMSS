package com.example.lab5.history

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SensorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SensorRepository(SensorDatabase.get(application).sensorDao())
    val measurements = repository.measurements.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val activations = repository.activations.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    private val writeError = MutableStateFlow(false)
    val hasWriteError = writeError.asStateFlow()
    private var latestLux: Float? = null
    private var sampling: Job? = null

    fun onLux(lux: Float?) {
        latestLux = lux?.takeIf { it.isFinite() && it >= 0f }
    }

    fun start() {
        if (sampling?.isActive == true) return
        sampling = viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                val lux = latestLux ?: continue
                save(SensorRecord(timestamp = System.currentTimeMillis(), sensorValue = lux, actionTriggered = false))
            }
        }
    }

    fun stop() {
        sampling?.cancel()
        sampling = null
        latestLux = null
    }

    fun onActivation(lux: Float?) {
        val record = SensorRecord(
            timestamp = System.currentTimeMillis(),
            sensorValue = lux?.takeIf { it.isFinite() && it >= 0f },
            actionTriggered = true,
        )
        viewModelScope.launch { save(record) }
    }

    private suspend fun save(record: SensorRecord) {
        try {
            repository.record(record)
            writeError.value = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: android.database.SQLException) {
            writeError.value = true
            Log.e("LightHistory", "Unable to save history record", error)
        }
    }
}
