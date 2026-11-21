package com.example.lab4.control

data class Decision(val lampOn: Boolean, val reason: String)

/** Low light turns the work lamp on; hysteresis prevents flicker near 300 lx. */
class DecisionEngine(val onBelow: Float = 300f, val offAt: Float = 350f) {
    init { require(onBelow.isFinite() && offAt.isFinite() && onBelow >= 0 && offAt > onBelow) }
    private var active = false
    fun reset() { active = false }
    fun evaluate(lux: Float?): Decision {
        if (lux == null || !lux.isFinite() || lux < 0) {
            active = false
            return Decision(false, "Немає коректного вимірювання: OFF")
        }
        active = if (active) lux < offAt else lux < onBelow
        return Decision(active, when {
            lux < onBelow -> "Освітленість < 300 лк: допоміжне світло ON"
            lux >= offAt -> "Освітленість ≥ 350 лк: світло OFF"
            else -> "300–350 лк: збережено попередній стан"
        })
    }
}
