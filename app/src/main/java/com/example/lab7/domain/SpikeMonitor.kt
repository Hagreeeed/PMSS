package com.example.lab7.domain

import kotlin.math.abs
import kotlin.math.max

data class Spike(val peakLux: Float, val ticks: Int)

/**
 * Знаходить короткі викиди, які медіанний фільтр не пропустив далі.
 * Викид: сире значення сильно відхиляється від відфільтрованого, а після його завершення
 * відфільтроване значення лишається на попередньому рівні. Справжній перехід сюди не потрапляє,
 * бо після нього фільтр переходить на новий рівень.
 */
class SpikeMonitor(private val relative: Float = 0.5f, private val absolute: Float = 50f) {
    private var levelBefore: Float? = null
    private var peak = 0f
    private var ticks = 0

    fun update(raw: Float, filtered: Float): Spike? {
        if (far(raw, filtered)) {
            if (levelBefore == null) {
                levelBefore = filtered
                peak = raw
                ticks = 0
            }
            ticks++
            if (abs(raw - filtered) > abs(peak - filtered)) peak = raw
            return null
        }
        val before = levelBefore ?: return null
        levelBefore = null
        return if (far(filtered, before)) null else Spike(peak, ticks)
    }

    fun reset() { levelBefore = null }

    private fun far(a: Float, b: Float) = abs(a - b) > max(absolute, relative * max(a, b))
}
