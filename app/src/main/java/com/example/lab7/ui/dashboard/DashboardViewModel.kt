package com.example.lab7.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab7.data.ble.IBleConnector
import com.example.lab7.domain.ReadingRepository
import com.example.lab7.domain.SmartPipeline
import com.example.lab7.domain.model.SyncPhase
import com.example.lab7.ui.common.factory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val pipeline: SmartPipeline,
    private val ble: IBleConnector,
    readings: ReadingRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = mutableState.asStateFlow()
    private val events = Channel<DashboardEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch {
            var lastPhase: SyncPhase? = null
            pipeline.state.collect { s ->
                mutableState.update {
                    it.copy(
                        running = s.running, rawLux = s.sensors.lux, filteredLux = s.filteredLux,
                        prediction = s.prediction, windowFill = s.windowFill, windowSize = s.windowSize,
                        modelError = s.modelError, critical = s.critical, mode = s.mode, lampOn = s.lampOn,
                        lampReason = s.lampReason, activeRules = s.activeRules.map { r -> r.title },
                        recordIntervalMs = s.recordIntervalMs, cloud = s.cloud, storageError = s.storageError,
                        filteredSpikes = s.filteredSpikes,
                    )
                }
                if (s.cloud.phase == SyncPhase.OFFLINE && lastPhase != null && lastPhase != SyncPhase.OFFLINE) {
                    events.send(DashboardEvent.Message("Немає мережі. Дані зберігаються локально (Pending)"))
                }
                lastPhase = s.cloud.phase
            }
        }
        viewModelScope.launch { ble.state.collect { b -> mutableState.update { it.copy(blePhase = b.phase) } } }
        viewModelScope.launch { readings.latest(5).collect { list -> mutableState.update { it.copy(latest = list) } } }
    }

    fun onAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.SetMode -> pipeline.setMode(action.mode)
            is DashboardAction.Lamp -> pipeline.manualLamp(action.on)
            DashboardAction.TestVibration -> pipeline.testVibration()
            DashboardAction.ConnectHub -> ble.quickConnect()
            DashboardAction.DisconnectHub -> ble.disconnect()
            DashboardAction.SyncNow -> pipeline.syncNow()
        }
    }

    companion object {
        val Factory = factory { DashboardViewModel(it.pipeline, it.ble, it.readings) }
    }
}
