package com.example.lab7.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab7.domain.ReadingRepository
import com.example.lab7.ui.common.factory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AnalyticsViewModel(readings: ReadingRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { readings.measurements(50).collect { list -> mutableState.update { it.copy(measurements = list) } } }
        viewModelScope.launch { readings.events(30).collect { list -> mutableState.update { it.copy(events = list) } } }
    }

    companion object {
        val Factory = factory { AnalyticsViewModel(it.readings) }
    }
}
