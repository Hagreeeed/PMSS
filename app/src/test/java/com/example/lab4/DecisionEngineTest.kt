package com.example.lab4

import com.example.lab4.control.DecisionEngine
import org.junit.Assert.*
import org.junit.Test

class DecisionEngineTest {
    @Test fun hysteresisAndBoundaries() {
        val engine = DecisionEngine()
        assertFalse(engine.evaluate(300f).lampOn)
        assertTrue(engine.evaluate(299.9f).lampOn)
        assertTrue(engine.evaluate(300f).lampOn)
        assertTrue(engine.evaluate(349.9f).lampOn)
        assertFalse(engine.evaluate(350f).lampOn)
        assertFalse(engine.evaluate(320f).lampOn)
    }
    @Test fun missingInvalidAndResetAreOff() {
        val engine = DecisionEngine()
        for (value in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            engine.evaluate(10f)
            assertFalse(engine.evaluate(value).lampOn)
        }
        engine.evaluate(10f);engine.reset()
        assertFalse(engine.evaluate(320f).lampOn)
        assertTrue(engine.evaluate(0f).lampOn)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidThresholds() { DecisionEngine(350f, 300f) }
}
