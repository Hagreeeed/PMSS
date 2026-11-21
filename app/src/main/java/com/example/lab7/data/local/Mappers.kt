package com.example.lab7.data.local

import com.example.lab7.data.remote.ReadingDto
import com.example.lab7.domain.model.LightClass
import com.example.lab7.domain.model.Reading
import com.example.lab7.domain.model.RuleAction
import com.example.lab7.domain.model.RuleMetric
import com.example.lab7.domain.model.RuleOperator
import com.example.lab7.domain.model.SmartRule

fun ReadingEntity.toDomain() = Reading(
    id = id,
    sampleId = sampleId,
    timestamp = timestamp,
    rawLux = rawLux,
    lux = lux,
    lightClass = lightClass?.let { name -> LightClass.entries.firstOrNull { it.name == name } },
    confidence = confidence,
    event = event,
    synced = synced,
)

fun Reading.toEntity() = ReadingEntity(
    sampleId = sampleId,
    timestamp = timestamp,
    rawLux = rawLux,
    lux = lux,
    lightClass = lightClass?.name,
    confidence = confidence,
    event = event,
    isEvent = event != null,
    synced = false,
)

fun ReadingEntity.toDto(deviceId: String) = ReadingDto(
    sampleId = sampleId,
    deviceId = deviceId,
    timestamp = timestamp,
    type = "light",
    value = lux,
    rawValue = rawLux,
    state = lightClass,
    confidence = confidence,
    event = event,
)

fun RuleEntity.toDomain(): SmartRule? {
    return SmartRule(
        id = id,
        metric = RuleMetric.entries.firstOrNull { it.name == metric } ?: return null,
        operator = RuleOperator.entries.firstOrNull { it.name == operator } ?: return null,
        threshold = threshold,
        action = RuleAction.entries.firstOrNull { it.name == action } ?: return null,
        enabled = enabled,
    )
}

fun SmartRule.toEntity() = RuleEntity(id, metric.name, operator.name, threshold, action.name, enabled)
