package com.example.lab7.ui.rules

import com.example.lab7.domain.model.RuleAction
import com.example.lab7.domain.model.RuleMetric
import com.example.lab7.domain.model.RuleOperator
import com.example.lab7.domain.model.SmartRule

data class RulesState(
    val rules: List<SmartRule> = emptyList(),
    val activeIds: Set<Long> = emptySet(),
    val metric: RuleMetric = RuleMetric.LUX,
    val operator: RuleOperator = RuleOperator.LESS,
    val threshold: String = "",
    val action: RuleAction = RuleAction.VIBRATE,
    val thresholdError: String? = null,
)

sealed interface RulesAction {
    data class SelectMetric(val metric: RuleMetric) : RulesAction
    data class SelectOperator(val operator: RuleOperator) : RulesAction
    data class ThresholdChanged(val text: String) : RulesAction
    data class SelectAction(val action: RuleAction) : RulesAction
    data object Add : RulesAction
    data class Toggle(val rule: SmartRule) : RulesAction
    data class Delete(val id: Long) : RulesAction
}

sealed interface RulesEvent {
    data class Message(val text: String) : RulesEvent
}
