package com.example.lab7.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab7.domain.RuleRepository
import com.example.lab7.domain.SmartPipeline
import com.example.lab7.domain.model.SmartRule
import com.example.lab7.ui.common.factory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RulesViewModel(private val repository: RuleRepository, pipeline: SmartPipeline) : ViewModel() {
    private val mutableState = MutableStateFlow(RulesState())
    val state: StateFlow<RulesState> = mutableState.asStateFlow()
    private val events = Channel<RulesEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init {
        viewModelScope.launch { repository.rules.collect { rules -> mutableState.update { it.copy(rules = rules) } } }
        viewModelScope.launch {
            pipeline.state.collect { s -> mutableState.update { it.copy(activeIds = s.activeRules.map { r -> r.id }.toSet()) } }
        }
    }

    fun onAction(action: RulesAction) {
        when (action) {
            is RulesAction.SelectMetric -> mutableState.update { it.copy(metric = action.metric) }
            is RulesAction.SelectOperator -> mutableState.update { it.copy(operator = action.operator) }
            is RulesAction.SelectAction -> mutableState.update { it.copy(action = action.action) }
            is RulesAction.ThresholdChanged -> mutableState.update { it.copy(threshold = action.text, thresholdError = null) }
            RulesAction.Add -> add()
            is RulesAction.Toggle -> viewModelScope.launch { repository.upsert(action.rule.copy(enabled = !action.rule.enabled)) }
            is RulesAction.Delete -> viewModelScope.launch { repository.delete(action.id) }
        }
    }

    private fun add() {
        val s = state.value
        val threshold = s.threshold.replace(',', '.').toFloatOrNull()
        if (threshold == null || !threshold.isFinite()) {
            mutableState.update { it.copy(thresholdError = "Введіть число") }
            return
        }
        val rule = SmartRule(metric = s.metric, operator = s.operator, threshold = threshold, action = s.action)
        viewModelScope.launch {
            repository.upsert(rule)
            mutableState.update { it.copy(threshold = "") }
            events.send(RulesEvent.Message("Додано: ${rule.title}"))
        }
    }

    companion object {
        val Factory = factory { RulesViewModel(it.rules, it.pipeline) }
    }
}
