package com.example.lab7.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab7.BuildConfig
import com.example.lab7.data.ble.IBleConnector
import com.example.lab7.domain.SmartPipeline
import com.example.lab7.ui.common.factory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DevicesViewModel(private val ble: IBleConnector, private val pipeline: SmartPipeline) : ViewModel() {
    private val mutableState = MutableStateFlow(DevicesState(hubUrl = BuildConfig.HUB_URL))
    val state: StateFlow<DevicesState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { ble.state.collect { b -> mutableState.update { it.copy(ble = b) } } }
        viewModelScope.launch {
            pipeline.state.collect { s ->
                mutableState.update { it.copy(cloud = s.cloud, mode = s.mode, remoteStatus = s.remoteStatus, log = s.log) }
            }
        }
    }

    fun onAction(action: DevicesAction) {
        when (action) {
            DevicesAction.Scan -> ble.scan()
            DevicesAction.Connect -> ble.connect()
            DevicesAction.Disconnect -> ble.disconnect()
            DevicesAction.Read -> ble.readData()
            DevicesAction.ToggleNotify -> ble.setNotifications(!state.value.ble.notificationsEnabled)
            DevicesAction.Interference -> ble.simulateInterference()
            DevicesAction.SyncNow -> pipeline.syncNow()
        }
    }

    companion object {
        val Factory = factory { DevicesViewModel(it.ble, it.pipeline) }
    }
}
