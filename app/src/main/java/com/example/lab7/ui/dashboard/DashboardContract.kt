package com.example.lab7.ui.dashboard

import com.example.lab7.data.ble.BlePhase
import com.example.lab7.domain.model.CloudStatus
import com.example.lab7.domain.model.ControlMode
import com.example.lab7.domain.model.Prediction
import com.example.lab7.domain.model.Reading

data class DashboardState(
    val running: Boolean = false,
    val rawLux: Float? = null,
    val filteredLux: Float? = null,
    val prediction: Prediction? = null,
    val windowFill: Int = 0,
    val windowSize: Int = 20,
    val modelError: Boolean = false,
    val critical: Boolean = false,
    val mode: ControlMode = ControlMode.AUTO,
    val lampOn: Boolean = false,
    val lampReason: String = "",
    val activeRules: List<String> = emptyList(),
    val recordIntervalMs: Long = 2_000,
    val cloud: CloudStatus = CloudStatus(),
    val blePhase: BlePhase = BlePhase.DISCONNECTED,
    val latest: List<Reading> = emptyList(),
    val storageError: String? = null,
    val filteredSpikes: Int = 0,
)

sealed interface DashboardAction {
    data class SetMode(val mode: ControlMode) : DashboardAction
    data class Lamp(val on: Boolean) : DashboardAction
    data object TestVibration : DashboardAction
    data object ConnectHub : DashboardAction
    data object DisconnectHub : DashboardAction
    data object SyncNow : DashboardAction
}

sealed interface DashboardEvent {
    data class Message(val text: String) : DashboardEvent
}
