package com.example.lab7

import com.example.lab7.domain.SmartPipeline
import com.example.lab7.domain.model.ControlMode
import com.example.lab7.domain.model.RuleAction
import com.example.lab7.domain.model.RuleMetric
import com.example.lab7.domain.model.RuleOperator
import com.example.lab7.domain.model.SmartRule
import com.example.lab7.domain.model.SyncPhase
import com.example.lab7.domain.model.Acceleration
import com.example.lab7.domain.RemoteCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Наскрізні сценарії ЛР7 на фейках, у віртуальному часі. */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartPipelineTest {
    private val sensors = FakeSensors()
    private val classifier = FakeClassifier()
    private val readings = FakeReadings()
    private val network = FakeNetwork()
    private val actuator = FakeActuator()
    private val hub = FakeHub()
    private val remote = FakeRemote()

    private fun TestScope.pipeline(rules: List<SmartRule> = emptyList()): SmartPipeline {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return SmartPipeline(
            sensors, { classifier }, readings, FakeRules(rules), network, actuator, hub, remote,
            scope = backgroundScope, computeDispatcher = dispatcher, clock = { testScheduler.currentTime },
        )
    }

    @Test fun lowLightActivatesLampVibrationHubAndEvent() = runTest {
        val system = pipeline()
        sensors.lux(600f)
        system.start()
        advanceTimeBy(3_000); runCurrent()
        assertFalse(system.state.value.lampOn)

        sensors.lux(200f)
        advanceTimeBy(3_000); runCurrent()
        assertTrue(system.state.value.critical)
        assertTrue(actuator.lamp)
        assertEquals(1, actuator.vibrations)
        assertTrue("LAMP:ON" in hub.sent)
        assertTrue(readings.stored.value.any { it.event?.startsWith("Лампа ON") == true })
    }

    @Test fun shortSpikeIsFilteredAndDoesNotTriggerActuator() = runTest {
        val system = pipeline()
        sensors.lux(500f)
        system.start()
        advanceTimeBy(3_000); runCurrent()
        sensors.lux(20f)
        advanceTimeBy(150); runCurrent() // викид довжиною 3 такти по 50 мс
        sensors.lux(500f)
        advanceTimeBy(3_000); runCurrent()
        assertFalse(system.state.value.lampOn)
        assertEquals(0, actuator.vibrations)
        assertTrue(readings.stored.value.none { it.isEvent })
    }

    @Test fun briefDarknessIsNotConfirmedAsShadow() = runTest {
        val system = pipeline()
        sensors.lux(600f)
        system.start()
        advanceTimeBy(3_000); runCurrent()
        sensors.lux(20f)
        advanceTimeBy(400); runCurrent() // рука над датчиком на 0,4 с
        sensors.lux(600f)
        advanceTimeBy(3_000); runCurrent()
        assertFalse(system.state.value.lampOn)
        assertTrue(readings.stored.value.none { it.isEvent })
    }

    @Test fun offlineReadingsStayPendingAndSyncAfterReconnect() = runTest {
        network.online.value = false
        val system = pipeline()
        sensors.lux(500f)
        system.start()
        advanceTimeBy(20_000); runCurrent()
        val pending = readings.stored.value.count { !it.synced }
        assertTrue(pending >= 8)
        assertEquals(0, readings.uploads)
        assertEquals(SyncPhase.OFFLINE, system.state.value.cloud.phase)

        network.online.value = true
        advanceTimeBy(100); runCurrent()
        assertTrue(readings.stored.value.all { it.synced })
        assertEquals(SyncPhase.SYNCED, system.state.value.cloud.phase)
    }

    @Test fun serverErrorKeepsRecordsForRetry() = runTest {
        readings.serverUp = false
        val system = pipeline()
        sensors.lux(500f)
        system.start()
        advanceTimeBy(6_000); runCurrent()
        assertEquals(SyncPhase.ERROR, system.state.value.cloud.phase)
        assertTrue(readings.stored.value.none { it.synced })
        readings.serverUp = true
        advanceTimeBy(5_000); runCurrent()
        assertEquals(SyncPhase.SYNCED, system.state.value.cloud.phase)
        assertTrue(readings.uploads >= 3)
    }

    @Test fun aiShadowTurnsLampOnAboveThreshold() = runTest {
        val system = pipeline()
        sensors.lux(1_700f)
        system.start()
        advanceTimeBy(3_000); runCurrent()
        sensors.lux(380f) // вище порога 350 лк, але різкий спад
        advanceTimeBy(1_500); runCurrent()
        assertTrue(system.state.value.lampOn)
        assertEquals("AI: різке затемнення", system.state.value.lampReason)
        advanceTimeBy(12_000); runCurrent()
        assertFalse(system.state.value.lampOn)
    }

    @Test fun userRuleVibratesOnceWhenAccelerationXAboveTen() = runTest {
        val rule = SmartRule(1, RuleMetric.ACCEL_X, RuleOperator.GREATER, 10f, RuleAction.VIBRATE)
        val system = pipeline(listOf(rule))
        sensors.lux(500f)
        system.start()
        advanceTimeBy(1_000); runCurrent()
        sensors.snapshot.value = sensors.snapshot.value.copy(acceleration = Acceleration(12f, 0f, 9.8f))
        advanceTimeBy(2_000); runCurrent()
        assertEquals(1, actuator.vibrations)
        assertTrue(readings.stored.value.any { it.event?.contains("Прискорення X > 10") == true })
        assertFalse(system.state.value.lampOn)
    }

    @Test fun fullStorageDoesNotStopActuators() = runTest {
        readings.failWrites = true
        val system = pipeline()
        sensors.lux(600f)
        system.start()
        advanceTimeBy(3_000); runCurrent()
        assertNotNull(system.state.value.storageError)
        sensors.lux(100f)
        advanceTimeBy(3_000); runCurrent()
        assertTrue(actuator.lamp)
        readings.failWrites = false
        advanceTimeBy(3_000); runCurrent()
        assertNull(system.state.value.storageError)
    }

    @Test fun remoteModeFollowsHubCommandAndStopReleasesEverything() = runTest {
        val system = pipeline()
        sensors.lux(600f)
        system.start()
        system.setMode(ControlMode.REMOTE)
        remote.command = RemoteCommand(5, true)
        advanceTimeBy(3_500); runCurrent()
        assertTrue(system.state.value.lampOn)
        system.stop()
        runCurrent()
        assertFalse(actuator.lamp)
        assertFalse(system.state.value.running)
        assertTrue(classifier.closed)
    }
}
