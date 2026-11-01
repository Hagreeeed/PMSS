package com.example.lab3.ui

import kotlin.math.hypot

data class TiltPosition(val x: Float, val y: Float)

/** Проєкція X/Y: 1 g відповідає краю індикатора; кулька не виходить за коло. */
fun tiltPosition(x: Float, y: Float): TiltPosition {
    if (!x.isFinite() || !y.isFinite()) return TiltPosition(0f, 0f)
    val normalizedX = x.toDouble() / 9.80665
    val normalizedY = -y.toDouble() / 9.80665
    val scale = maxOf(1.0, hypot(normalizedX, normalizedY))
    return TiltPosition((normalizedX / scale).toFloat(), (normalizedY / scale).toFloat())
}
