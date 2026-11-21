package com.example.lab7.ui.sensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab7.domain.SmartPipeline
import com.example.lab7.ui.common.factory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SensorsViewModel(pipeline: SmartPipeline) : ViewModel() {
    private val mutableState = MutableStateFlow(SensorsState())
    val state: StateFlow<SensorsState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { pipeline.state.collect { mutableState.value = SensorsState(it.running, it.sensors) } }
    }

    companion object {
        val Factory = factory { SensorsViewModel(it.pipeline) }
    }
}
