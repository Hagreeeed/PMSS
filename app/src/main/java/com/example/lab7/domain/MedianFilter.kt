package com.example.lab7.domain

/**
 * Медіанний фільтр для освітленості. Короткий викид (менше половини вікна) не проходить далі,
 * а різкий перехід між рівнями зберігається, тому модель ЛР6 і далі бачить «сходинку».
 */
class MedianFilter(private val size: Int = 9) {
    init { require(size % 2 == 1 && size > 0) }

    private val values = ArrayDeque<Float>(size)

    fun add(value: Float): Float {
        if (values.size == size) values.removeFirst()
        values.addLast(value)
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    fun reset() = values.clear()
}
