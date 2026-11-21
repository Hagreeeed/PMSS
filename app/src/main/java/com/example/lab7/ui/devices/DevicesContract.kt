package com.example.lab7.ui.devices

import com.example.lab7.data.ble.BleUiState
import com.example.lab7.domain.model.CloudStatus
import com.example.lab7.domain.model.ControlMode

data class DevicesState(
    val ble: BleUiState = BleUiState(),
    val cloud: CloudStatus = CloudStatus(),
    val mode: ControlMode = ControlMode.AUTO,
    val remoteStatus: String = "—",
    val hubUrl: String = "",
    val log: List<String> = emptyList(),
)

sealed interface DevicesAction {
    data object Scan : DevicesAction
    data object Connect : DevicesAction
    data object Disconnect : DevicesAction
    data object Read : DevicesAction
    data object ToggleNotify : DevicesAction
    data object Interference : DevicesAction
    data object SyncNow : DevicesAction
}
