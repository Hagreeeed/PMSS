package com.example.lab7.domain

data class Decision(val lowLight: Boolean, val reason: String)

/**
 * Поріг недостатнього освітлення з гістерезисом (ON нижче 300 лк, OFF від 350 лк).
 * Новий стан приймається тільки після того, як умова тримається [confirmMs].
 */
class DecisionEngine(
    private val onBelow: Float = 300f,
    private val offAt: Float = 350f,
    private val confirmMs: Long = 1_000,
) {
    init { require(onBelow >= 0f && offAt > onBelow && confirmMs >= 0) }

    private var active = false
    private var candidate: Boolean? = null
    private var candidateSince = 0L

    fun reset() {
        active = false
        candidate = null
    }

    fun evaluate(lux: Float?, now: Long): Decision {
        if (lux == null || !lux.isFinite() || lux < 0f) {
            reset()
            return Decision(false, "Немає даних датчика")
        }
        val wanted = if (active) lux < offAt else lux < onBelow
        if (wanted == active) {
            candidate = null
        } else if (candidate != wanted) {
            candidate = wanted
            candidateSince = now
        } else if (now - candidateSince >= confirmMs) {
            active = wanted
            candidate = null
        }
        val reason = when {
            candidate != null -> "Перевірка стану (${confirmMs / 1000.0} с)"
            active -> "Мало світла: < ${onBelow.toInt()} лк"
            lux >= offAt -> "Достатньо світла: ≥ ${offAt.toInt()} лк"
            else -> "${onBelow.toInt()}–${offAt.toInt()} лк: стан без змін"
        }
        return Decision(active, reason)
    }
}
