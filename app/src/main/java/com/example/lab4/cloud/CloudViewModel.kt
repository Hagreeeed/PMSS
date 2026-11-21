package com.example.lab4.cloud

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab4.BuildConfig
import com.example.lab4.sensors.SensorUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class CloudViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AndroidCloudStore(application)
    private val network = NetworkStatus(application)
    private val repository = CloudRepository(
        CloudClient.create(logger = { Log.d("LightHttp", it) }, debug = BuildConfig.DEBUG),
        store, logger = { Log.i("LightCloud", it) },
    )
    val state = repository.state
    private val notices = Channel<String>(Channel.CONFLATED)
    val messages = notices.receiveAsFlow()
    private var latest = SensorUiState()
    private var syncJob: Job? = null

    fun onSensorsChanged(sensors: SensorUiState) { latest = sensors }

    fun start() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            try { repository.restore() } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notices.trySend("Не вдалося відновити локальну чергу")
            }
            var previousPending = false
            while (true) {
                delay(5_000)
                val sample = latest.lux?.takeIf { latest.foreground }?.let { lux ->
                    SensorData(store.deviceId, System.currentTimeMillis(), lux,
                        x = latest.acceleration?.x, y = latest.acceleration?.y, z = latest.acceleration?.z)
                }
                repository.cycle(sample, network.isOnline())
                val pending = state.value.phase == SyncPhase.PENDING
                if (pending && !previousPending) notices.trySend(state.value.message)
                previousPending = pending
            }
        }
    }

    fun stop() { syncJob?.cancel(); syncJob = null }
}
