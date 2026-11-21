package com.example.lab6.history

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
import kotlinx.coroutines.flow.collectLatest
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
    private val samplingInterval = MutableStateFlow(2_000L)
    val interval = samplingInterval.asStateFlow()

    fun setFastSampling(fast: Boolean) {
        samplingInterval.value = if (fast) 500L else 2_000L
    }

    fun onLux(lux: Float?) {
        latestLux = lux?.takeIf { it.isFinite() && it >= 0f }
    }

    fun start() {
        if (sampling?.isActive == true) return
        sampling = viewModelScope.launch {
            samplingInterval.collectLatest { period ->
                while (isActive) {
                    delay(period)
                    val lux = latestLux ?: continue
                    save(SensorRecord(timestamp = System.currentTimeMillis(), sensorValue = lux, actionTriggered = false))
                }
            }
        }
    }

    fun stop() {
        sampling?.cancel()
        sampling = null
        latestLux = null
        setFastSampling(false)
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
