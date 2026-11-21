package com.example.lab3.ble

import java.util.Locale
import java.util.UUID

/** Custom GATT profile. These UUIDs identify the mock protocol, not Bluetooth SIG services. */
object BleProfile {
    val SERVICE_UUID: UUID = UUID.fromString("8f4b1000-6a6b-4d91-a922-42d0a62f0100")
    val STATUS_UUID: UUID = UUID.fromString("8f4b1001-6a6b-4d91-a922-42d0a62f0100")
    val COMMAND_UUID: UUID = UUID.fromString("8f4b1002-6a6b-4d91-a922-42d0a62f0100")
    const val MAX_PACKET_BYTES = 20 // Fits the ATT payload of the baseline MTU 23.
    val DEVICE = BleDevice("02:00:00:00:00:02", "Light Smart Hub", -48)

    fun luxPacket(lux: Float): String? =
        if (lux.isFinite() && lux in 0f..200_000f) "LUX:${decimal(lux)}" else null

    fun decimal(value: Float): String = String.format(Locale.US, "%.1f", value)
}

data class BleDevice(val address: String, val name: String, val rssi: Int)
enum class BlePhase { DISCONNECTED, SCANNING, CONNECTING, CONNECTED, DATA_TRANSFER }
data class BleLog(val id: Long, val time: String, val direction: String, val message: String)

data class BleUiState(
    val phase: BlePhase = BlePhase.DISCONNECTED,
    val devices: List<BleDevice> = emptyList(),
    val notificationsEnabled: Boolean = false,
    val lastRead: String = "—",
    val lastNotification: String = "—",
    val logs: List<BleLog> = emptyList(),
)
