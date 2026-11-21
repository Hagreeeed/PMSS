package com.example.lab4

import com.example.lab4.control.*
import org.junit.Assert.*
import org.junit.Test

class ActuatorControllerTest {
    private class Driver : ActuatorPort {
        var on = false; var pulses = 0; var stops = 0
        override fun triggerAlarm() { pulses++ }
        override fun toggleFlashlight(status: Boolean) { on = status }
        override fun stop() { on = false; stops++ }
    }
    @Test fun sensorBurstDoesNotRepeatVibration() {
        val d = Driver();val c = ActuatorController(d);c.start()
        repeat(100) { c.onLux(50f) }
        assertTrue(d.on); assertEquals(1,d.pulses)
        c.onLux(350f);c.onLux(50f)
        assertEquals(2,d.pulses)
    }
    @Test fun modesHaveExclusiveAuthority() {
        val d = Driver();val c = ActuatorController(d);c.start();c.select(ControlMode.MANUAL)
        c.manual(true);c.onLux(900f);c.remote(RemoteCommand(1,false),"{}")
        assertTrue(d.on)
        c.select(ControlMode.REMOTE);assertFalse(d.on)
        c.manual(true);c.onLux(0f);assertFalse(d.on)
        c.remote(RemoteCommand(2,true),"{\"completed\":true}");assertTrue(d.on)
        c.remote(RemoteCommand(2,true),"{}");assertEquals(2,d.pulses)
        c.remote(RemoteCommand(3,false),"{}");assertFalse(d.on)
    }
    @Test fun stopRejectsLateCommandsAndResumeRequiresFreshDecision() {
        val d = Driver();val c = ActuatorController(d);c.start();c.select(ControlMode.REMOTE)
        c.remote(RemoteCommand(1,true),"{}");c.stop()
        c.remote(RemoteCommand(2,true),"{}");c.manual(true);c.onLux(0f)
        assertFalse(d.on);assertFalse(c.state.value.lampOn)
        c.start();assertFalse(d.on)
        c.remote(RemoteCommand(2,true),"{}");assertTrue(d.on)
    }
    @Test fun networkErrorTurnsLampOffAndNextCommandRecovers() {
        val d = Driver();val c = ActuatorController(d);c.start();c.select(ControlMode.REMOTE)
        c.remote(RemoteCommand(1,true),"{}");c.remoteError("HTTP 503")
        assertFalse(d.on);assertTrue(c.state.value.remoteStatus.contains("503"))
        c.remote(RemoteCommand(1,true),"{}");assertTrue(d.on)
    }
    @Test fun returningToAutoResetsHysteresisAndUsesLatestLux() {
        val d = Driver();val c = ActuatorController(d);c.start();c.onLux(10f)
        c.select(ControlMode.MANUAL);c.onLux(320f);c.select(ControlMode.AUTO)
        assertFalse(d.on)
        c.onLux(10f);assertTrue(d.on)
        c.onLux(null);assertFalse(d.on)
    }
}
