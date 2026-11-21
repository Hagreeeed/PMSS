package com.example.lab6.ai

import kotlin.math.ln1p

internal const val LIGHT_WINDOW_SIZE = 20
private const val MAX_LUX = 200_000f

internal class LightWindow {
    private val samples = FloatArray(LIGHT_WINDOW_SIZE)
    private var next = 0
    var count = 0
        private set

    fun add(lux: Float) {
        samples[next] = ln1p(lux.coerceIn(0f, MAX_LUX)) / ln1p(MAX_LUX)
        next = (next + 1) % samples.size
        if (count < samples.size) count++
    }

    fun snapshot(): FloatArray {
        check(count == samples.size)
        return FloatArray(samples.size) { index -> samples[(next + index) % samples.size] }
    }

    fun clear() {
        next = 0
        count = 0
    }
}
