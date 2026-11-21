package com.example.lab7

import com.example.lab7.data.remote.parseCommand
import com.example.lab7.domain.DecisionEngine
import com.example.lab7.domain.MedianFilter
import com.example.lab7.domain.RuleEngine
import com.example.lab7.domain.SpikeMonitor
import com.example.lab7.domain.model.Acceleration
import com.example.lab7.domain.model.RuleAction
import com.example.lab7.domain.model.RuleMetric
import com.example.lab7.domain.model.RuleOperator
import com.example.lab7.domain.model.SensorSnapshot
import com.example.lab7.domain.model.SmartRule
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainLogicTest {
    @Test fun medianFilterDropsShortSpikeButKeepsStep() {
        val filter = MedianFilter(9)
        repeat(9) { filter.add(500f) }
        // три викиди поспіль у вікні з дев'яти значень не змінюють медіану
        repeat(3) { assertEquals(500f, filter.add(20f)) }
        assertEquals(500f, filter.add(500f))
        // стійкий перехід проходить після половини вікна
        val after = (1..9).map { filter.add(100f) }
        assertEquals(100f, after.last())
    }

    @Test fun spikeMonitorReportsAbsorbedSpikeButNotRealStep() {
        val filter = MedianFilter(9)
        val monitor = SpikeMonitor()
        fun feed(raw: Float) = monitor.update(raw, filter.add(raw))
        repeat(9) { assertNull(feed(500f)) }
        repeat(3) { assertNull(feed(20f)) }
        val spike = feed(500f)
        assertEquals(20f, spike!!.peakLux)
        assertEquals(3, spike.ticks)
        // перехід на новий рівень не вважається викидом
        val results = (1..9).map { feed(100f) }
        assertTrue(results.all { it == null })
    }

    @Test fun decisionUsesHysteresisAndConfirmation() {
        val engine = DecisionEngine(onBelow = 300f, offAt = 350f, confirmMs = 1_000)
        assertFalse(engine.evaluate(250f, now = 0).lowLight)
        assertFalse(engine.evaluate(250f, now = 999).lowLight)
        assertTrue(engine.evaluate(250f, now = 1_000).lowLight)
        // 320 лк між порогами: стан не змінюється
        assertTrue(engine.evaluate(320f, now = 5_000).lowLight)
        assertTrue(engine.evaluate(400f, now = 6_000).lowLight)
        assertFalse(engine.evaluate(400f, now = 7_000).lowLight)
    }

    @Test fun shortDipDoesNotSwitchDecision() {
        val engine = DecisionEngine(confirmMs = 1_000)
        engine.evaluate(500f, 0)
        engine.evaluate(100f, 100)
        assertFalse(engine.evaluate(500f, 600).lowLight)
        assertFalse(engine.evaluate(100f, 700).lowLight)
        assertFalse(engine.evaluate(100f, 1_600).lowLight)
    }

    @Test fun ruleFiresOnceWhenConditionBecomesTrue() {
        val rule = SmartRule(1, RuleMetric.ACCEL_X, RuleOperator.GREATER, 10f, RuleAction.VIBRATE)
        val engine = RuleEngine()
        fun at(x: Float) = engine.evaluate(listOf(rule), 500f, SensorSnapshot(acceleration = Acceleration(x, 0f, 9.8f)))
        assertTrue(at(5f).fired.isEmpty())
        assertEquals(listOf(rule), at(12f).fired)
        assertTrue(at(13f).fired.isEmpty())
        assertEquals(listOf(rule), at(13f).active)
        assertTrue(at(4f).active.isEmpty())
        assertEquals(listOf(rule), at(11f).fired)
    }

    @Test fun disabledRuleAndMissingValueAreIgnored() {
        val engine = RuleEngine()
        val disabled = SmartRule(1, RuleMetric.LUX, RuleOperator.LESS, 100f, RuleAction.VIBRATE, enabled = false)
        val needsAccel = SmartRule(2, RuleMetric.ACCEL_Z, RuleOperator.GREATER, 0f, RuleAction.VIBRATE)
        assertTrue(engine.evaluate(listOf(disabled, needsAccel), 10f, SensorSnapshot()).active.isEmpty())
    }

    @Test fun hubCommandNeedsPositiveIdAndStrictBoolean() {
        assertTrue(parseCommand(JsonParser.parseString("{\"id\":1,\"completed\":true}").asJsonObject).lampOn)
        for (json in listOf("{}", "{\"id\":1,\"completed\":\"true\"}", "{\"id\":1.5,\"completed\":true}", "{\"id\":0,\"completed\":true}")) {
            assertTrue(runCatching { parseCommand(JsonParser.parseString(json).asJsonObject) }.isFailure)
        }
    }
}
