package com.example.lab7.domain

import com.example.lab7.domain.model.RuleMetric
import com.example.lab7.domain.model.RuleOperator
import com.example.lab7.domain.model.SensorSnapshot
import com.example.lab7.domain.model.SmartRule

data class RuleResult(
    /** Правила, умова яких виконується зараз. */
    val active: List<SmartRule>,
    /** Правила, умова яких щойно стала істинною: їхня дія виконується один раз. */
    val fired: List<SmartRule>,
)

/** Користувацькі сценарії «якщо метрика > / < поріг, то дія». */
class RuleEngine {
    private var previouslyActive = emptySet<Long>()

    fun evaluate(rules: List<SmartRule>, lux: Float?, sensors: SensorSnapshot): RuleResult {
        val active = rules.filter { it.enabled && matches(it, lux, sensors) }
        val ids = active.map { it.id }.toSet()
        val fired = active.filter { it.id !in previouslyActive }
        previouslyActive = ids
        return RuleResult(active, fired)
    }

    fun reset() { previouslyActive = emptySet() }

    private fun matches(rule: SmartRule, lux: Float?, sensors: SensorSnapshot): Boolean {
        val value = when (rule.metric) {
            RuleMetric.LUX -> lux
            RuleMetric.ACCEL_X -> sensors.acceleration?.x
            RuleMetric.ACCEL_Y -> sensors.acceleration?.y
            RuleMetric.ACCEL_Z -> sensors.acceleration?.z
        } ?: return false
        return when (rule.operator) {
            RuleOperator.GREATER -> value > rule.threshold
            RuleOperator.LESS -> value < rule.threshold
        }
    }
}
