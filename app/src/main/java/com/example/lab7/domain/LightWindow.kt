package com.example.lab7.domain

import kotlin.math.ln1p

/** Кільцевий буфер для моделі ЛР6: значення нормалізуються як ln(1 + lux) / ln(1 + 200000). */
class LightWindow(val size: Int) {
    private val samples = FloatArray(size)
    private val luxValues = FloatArray(size)
    private var next = 0
    var count = 0
        private set

    val full: Boolean get() = count == size

    fun add(lux: Float) {
        samples[next] = ln1p(lux.coerceIn(0f, MAX_LUX)) / ln1p(MAX_LUX)
        luxValues[next] = lux
        next = (next + 1) % size
        if (count < size) count++
    }

    /** Вікно від найстарішого значення до найновішого. */
    fun snapshot(): FloatArray {
        check(full)
        return FloatArray(size) { samples[(next + it) % size] }
    }

    /** Найбільша освітленість у вікні, лк. */
    fun maxLux(): Float = (0 until count).maxOfOrNull { luxValues[it] } ?: 0f

    fun clear() {
        next = 0
        count = 0
    }

    private companion object { const val MAX_LUX = 200_000f }
}
