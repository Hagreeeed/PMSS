package com.example.lab7.domain.model

import kotlin.math.sqrt

data class Acceleration(val x: Float, val y: Float, val z: Float) {
    val magnitude: Float get() = sqrt(x * x + y * y + z * z)
}

/** Останні значення з датчиків. null означає, що датчик ще нічого не надіслав. */
data class SensorSnapshot(
    val lux: Float? = null,
    val acceleration: Acceleration? = null,
    val lightAvailable: Boolean = false,
    val accelerometerAvailable: Boolean = false,
)

enum class LightClass(val label: String) {
    STABLE("Стабільне світло"),
    CHANGING("Зміна освітленості"),
    SHADOW("Різке затемнення"),
}

data class Prediction(val lightClass: LightClass, val probabilities: List<Float>) {
    val confidence: Float get() = probabilities[lightClass.ordinal]
    /** Клас, якому система довіряє (впевненість від 80 %). */
    val accepted: LightClass? get() = lightClass.takeIf { confidence >= MIN_CONFIDENCE }

    companion object { const val MIN_CONFIDENCE = 0.8f }
}

enum class ControlMode { AUTO, MANUAL, REMOTE }

/** Один рядок історії: вимірювання або подія (ввімкнення лампи, спрацювання правила). */
data class Reading(
    val id: Long = 0,
    val sampleId: String,
    val timestamp: Long,
    val rawLux: Float?,
    val lux: Float?,
    val lightClass: LightClass?,
    val confidence: Float?,
    val event: String? = null,
    val synced: Boolean = false,
) {
    val isEvent: Boolean get() = event != null
}

enum class RuleMetric(val label: String, val unit: String) {
    LUX("Освітленість", "лк"),
    ACCEL_X("Прискорення X", "м/с²"),
    ACCEL_Y("Прискорення Y", "м/с²"),
    ACCEL_Z("Прискорення Z", "м/с²"),
}

enum class RuleOperator(val symbol: String) { GREATER(">"), LESS("<") }

enum class RuleAction(val label: String) {
    VIBRATE("вібрація"),
    LAMP_ON("увімкнути лампу"),
    BLE_ALERT("сигнал на BLE-хаб"),
}

data class SmartRule(
    val id: Long = 0,
    val metric: RuleMetric,
    val operator: RuleOperator,
    val threshold: Float,
    val action: RuleAction,
    val enabled: Boolean = true,
) {
    val title: String
        get() = "Якщо ${metric.label} ${operator.symbol} ${formatNumber(threshold)} ${metric.unit} → ${action.label}"
}

enum class SyncPhase { OFFLINE, IDLE, SYNCING, SYNCED, ERROR }

data class CloudStatus(
    val online: Boolean = false,
    val phase: SyncPhase = SyncPhase.IDLE,
    val pending: Int = 0,
    val lastSyncAt: Long? = null,
    val message: String = "Очікування",
)

fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
