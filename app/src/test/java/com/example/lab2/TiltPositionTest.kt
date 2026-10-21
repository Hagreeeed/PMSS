package com.example.lab2

import com.example.lab2.ui.tiltPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class TiltPositionTest {
    @Test fun flatDeviceKeepsBallAtCenter() {
        val position = tiltPosition(0f, 0f)
        assertEquals(0f, position.x, 0.0001f)
        assertEquals(0f, position.y, 0.0001f)
    }

    @Test fun positiveYMovesBallUp() {
        val position = tiltPosition(0f, 9.80665f)
        assertEquals(0f, position.x, 0.0001f)
        assertEquals(-1f, position.y, 0.0001f)
    }

    @Test fun diagonalAccelerationStaysInsideCircleAndPreservesDirection() {
        val position = tiltPosition(30f, 40f)
        assertEquals(1f, hypot(position.x, position.y), 0.0001f)
        assertEquals(0.6f, position.x, 0.0001f)
        assertEquals(-0.8f, position.y, 0.0001f)
    }

    @Test fun invalidInputDoesNotProduceInvalidCanvasCoordinates() {
        val position = tiltPosition(Float.NaN, Float.POSITIVE_INFINITY)
        assertTrue(position.x.isFinite() && position.y.isFinite())
    }
}
