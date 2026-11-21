package com.example.lab4.ble

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** All public calls and the supplied scope use Main. Only one GATT operation runs at a time. */
class MockBleConnector(
    private val scope: CoroutineScope,
    private val scanner: MockBleScanner = MockBleScanner(),
    private val logger: (String) -> Unit = {},
    private val connectionDelayMs: Long = 800,
) : IBleConnector {
    private val mutableState = MutableStateFlow(BleUiState())
    override val state = mutableState.asStateFlow()
    private var operation: Job? = null
    private var logId = 0L
    private var hubLux = 0f
    private var targetLux = 500

    override fun scan() {
        if (state.value.phase != BlePhase.DISCONNECTED) {
            log("ERROR", "Сканування доступне після відключення")
            return
        }
        mutableState.value = state.value.copy(phase = BlePhase.SCANNING, devices = emptyList())
        log("SCAN", "Пошук сервісу ${BleProfile.SERVICE_UUID}")
        operation = scope.launch {
            val devices = scanner.scan()
            mutableState.value = state.value.copy(phase = BlePhase.DISCONNECTED, devices = devices)
            log("SCAN", "Знайдено ${devices.size}: ${devices.first().name}")
        }
    }

    override fun connect() {
        if (state.value.phase != BlePhase.DISCONNECTED || state.value.devices.isEmpty()) {
            log("ERROR", "Спочатку знайдіть пристрій; повторне підключення заблоковане")
            return
        }
        mutableState.value = state.value.copy(phase = BlePhase.CONNECTING)
        log("STATE", "Connecting → ${BleProfile.DEVICE.name}")
        operation = scope.launch {
            try {
                withTimeout(3_000) { delay(connectionDelayMs) }
                mutableState.value = state.value.copy(phase = BlePhase.CONNECTED)
                log("STATE", "Connected; service discovered: ${BleProfile.SERVICE_UUID}")
            } catch (_: TimeoutCancellationException) {
                mutableState.value = state.value.copy(phase = BlePhase.DISCONNECTED)
                log("ERROR", "Тайм-аут підключення 3000 ms; можна повторити")
            }
        }
    }

    override fun disconnect() {
        operation?.cancel()
        operation = null
        val wasActive = state.value.phase != BlePhase.DISCONNECTED
        mutableState.value = state.value.copy(
            phase = BlePhase.DISCONNECTED, notificationsEnabled = false,
        )
        if (wasActive) log("STATE", "Disconnected; незавершену операцію скасовано")
    }

    override fun sendData(data: String) {
        if (state.value.phase != BlePhase.CONNECTED) {
            log("ERROR", "WRITE відхилено: немає з’єднання або канал зайнятий")
            return
        }
        if (data.toByteArray(Charsets.UTF_8).size > BleProfile.MAX_PACKET_BYTES) {
            log("ERROR", "Пакет перевищує 20 байтів")
            return
        }
        // Validate the complete packet before changing either the connection or the hub.
        val command = parseCommand(data)
        if (command == null) {
            log("ERROR", "Невірна команда: $data")
            return
        }
        transfer("WRITE", "${BleProfile.COMMAND_UUID} ← $data") {
            when (command) {
                is Command.Lux -> hubLux = command.value
                is Command.Target -> targetLux = command.value
                is Command.Notify -> mutableState.value = state.value.copy(
                    notificationsEnabled = command.enabled,
                )
            }
            log("RX", "ACK:${data.substringBefore(':')}")
            if (state.value.notificationsEnabled) notifyStatus()
        }
    }

    override fun readData() = transfer("READ", BleProfile.STATUS_UUID.toString()) {
        val payload = statusPayload()
        mutableState.value = state.value.copy(lastRead = payload)
        log("RX", "READ $payload")
    }

    override fun setNotifications(enabled: Boolean) = sendData(
        if (enabled) "NOTIFY:ON" else "NOTIFY:OFF",
    )

    override fun simulateInterference() {
        if (state.value.phase != BlePhase.CONNECTED && state.value.phase != BlePhase.DATA_TRANSFER) return
        disconnect()
        log("ERROR", "Імітація перешкоди: connection lost; підключіться повторно")
    }

    private fun transfer(kind: String, packet: String, complete: () -> Unit) {
        if (state.value.phase != BlePhase.CONNECTED) {
            log("ERROR", "$kind відхилено: немає з’єднання або канал зайнятий")
            return
        }
        mutableState.value = state.value.copy(phase = BlePhase.DATA_TRANSFER)
        log("TX", "$kind $packet")
        operation = scope.launch {
            delay(350)
            complete()
            mutableState.value = state.value.copy(phase = BlePhase.CONNECTED)
        }
    }

    private fun statusPayload() = "L:${BleProfile.decimal(hubLux)};T:$targetLux"

    private fun notifyStatus() {
        val payload = statusPayload()
        mutableState.value = state.value.copy(lastNotification = payload)
        log("NOTIFY", payload)
    }

    private fun log(direction: String, message: String) {
        val entry = BleLog(++logId, LocalTime.now().format(TIME_FORMAT), direction, message)
        mutableState.value = state.value.copy(logs = (state.value.logs + entry).takeLast(80))
        logger("${entry.direction} ${entry.message}")
    }

    private sealed interface Command {
        data class Lux(val value: Float) : Command
        data class Target(val value: Int) : Command
        data class Notify(val enabled: Boolean) : Command
    }

    private fun parseCommand(data: String): Command? = when {
        data == "NOTIFY:ON" -> Command.Notify(true)
        data == "NOTIFY:OFF" -> Command.Notify(false)
        data.matches(Regex("LUX:[0-9]+(\\.[0-9])?")) -> data.substringAfter(':').toFloatOrNull()
            ?.takeIf { it.isFinite() && it in 0f..200_000f }?.let { Command.Lux(it) }
        data.matches(Regex("SET:[0-9]+")) -> data.substringAfter(':').toIntOrNull()
            ?.takeIf { it in 10..10_000 }?.let { Command.Target(it) }
        else -> null
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
