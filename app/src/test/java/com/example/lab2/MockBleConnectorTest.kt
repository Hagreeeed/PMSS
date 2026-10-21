package com.example.lab2

import com.example.lab2.ble.BlePhase
import com.example.lab2.ble.BleProfile
import com.example.lab2.ble.MockBleConnector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MockBleConnectorTest {
    @Test fun scannerFindsHubOnlyAfterTwoSeconds() = runTest {
        val ble = MockBleConnector(this)
        ble.scan()
        assertEquals(BlePhase.SCANNING, ble.state.value.phase)
        advanceTimeBy(1_999)
        assertTrue(ble.state.value.devices.isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(BleProfile.DEVICE), ble.state.value.devices)
        assertEquals(BlePhase.DISCONNECTED, ble.state.value.phase)
    }

    @Test fun writeReadAndNotifyPreserveProtocolValues() = runTest {
        val ble = MockBleConnector(this)
        ble.scan(); advanceTimeBy(2_000); runCurrent()
        ble.connect(); advanceTimeBy(800); runCurrent()
        assertEquals(BlePhase.CONNECTED, ble.state.value.phase)
        ble.setNotifications(true); advanceTimeBy(350); runCurrent()
        ble.sendData("LUX:420.0"); advanceTimeBy(350); runCurrent()
        assertEquals("L:420.0;T:500", ble.state.value.lastNotification)
        ble.readData(); advanceTimeBy(350); runCurrent()
        assertEquals("L:420.0;T:500", ble.state.value.lastRead)
        assertTrue(ble.state.value.logs.any { it.message == "ACK:LUX" })
    }

    @Test fun disconnectCancelsScanAndConnection() = runTest {
        val ble = MockBleConnector(this)
        ble.scan(); advanceTimeBy(500); ble.disconnect()
        advanceTimeBy(3_000); runCurrent()
        assertTrue(ble.state.value.devices.isEmpty())
        ble.scan(); advanceTimeBy(2_000); runCurrent()
        ble.connect(); advanceTimeBy(100); ble.disconnect()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(BlePhase.DISCONNECTED, ble.state.value.phase)
        assertFalse(ble.state.value.logs.any { it.message.startsWith("Connected;") })
    }

    @Test fun lostConnectionDoesNotAcknowledgeOrApplyPendingWrite() = runTest {
        val ble = MockBleConnector(this)
        ble.scan(); advanceTimeBy(2_000); runCurrent()
        ble.connect(); advanceTimeBy(800); runCurrent()
        ble.sendData("LUX:420.0"); advanceTimeBy(100)
        ble.simulateInterference(); advanceTimeBy(1_000); runCurrent()
        assertFalse(ble.state.value.logs.any { it.message == "ACK:LUX" })
        ble.connect(); advanceTimeBy(800); runCurrent()
        ble.readData(); advanceTimeBy(350); runCurrent()
        assertEquals("L:0.0;T:500", ble.state.value.lastRead)
        assertFalse(ble.state.value.notificationsEnabled)
    }

    @Test fun invalidAndConcurrentWritesAreRejected() = runTest {
        val ble = MockBleConnector(this)
        ble.sendData("LUX:420.0")
        assertEquals(BlePhase.DISCONNECTED, ble.state.value.phase)
        ble.scan(); advanceTimeBy(2_000); runCurrent()
        ble.connect(); advanceTimeBy(800); runCurrent()
        for (packet in listOf("SET:0", "LUX:NaN", "LUX:-1", "LUX:200001", "X".repeat(21))) {
            ble.sendData(packet)
            assertEquals(BlePhase.CONNECTED, ble.state.value.phase)
        }
        ble.sendData("LUX:50.0")
        ble.sendData("LUX:1000.0") // Busy: cannot overtake the first packet.
        advanceTimeBy(350); runCurrent()
        ble.readData(); advanceTimeBy(350); runCurrent()
        assertEquals("L:50.0;T:500", ble.state.value.lastRead)
    }

    @Test fun connectionTimeoutAllowsRetry() = runTest {
        val ble = MockBleConnector(this, connectionDelayMs = 4_000)
        ble.scan(); advanceTimeBy(2_000); runCurrent()
        ble.connect(); advanceTimeBy(3_000); runCurrent()
        assertEquals(BlePhase.DISCONNECTED, ble.state.value.phase)
        assertTrue(ble.state.value.logs.last().message.contains("Тайм-аут"))
        ble.connect()
        assertEquals(BlePhase.CONNECTING, ble.state.value.phase)
        ble.disconnect()
    }
}
