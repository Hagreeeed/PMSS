package com.example.lab2.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.example.lab2.cloud.CloudViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lab2.ble.BlePhase
import com.example.lab2.ble.BleProfile
import com.example.lab2.ble.BleUiState
import com.example.lab2.ble.IBleConnector
import com.example.lab2.sensors.SensorUiState

@Composable
fun LabApp(sensors: SensorUiState, connector: IBleConnector, cloud: CloudViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val ble by connector.state.collectAsStateWithLifecycle()
    val cloudState by cloud.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(cloud) {
        cloud.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Хмара") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("BLE") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Сенсори") })
            }
            when (tab) {
                0 -> CloudScreen(cloudState, sensors)
                1 -> HubScreen(sensors, ble, connector)
                else -> LightExposureScreen(sensors)
            }
        }
    }
}

@Composable
private fun HubScreen(sensors: SensorUiState, state: BleUiState, connector: IBleConnector) {
    val connected = state.phase == BlePhase.CONNECTED
    val busy = state.phase == BlePhase.SCANNING || state.phase == BlePhase.CONNECTING ||
        state.phase == BlePhase.DATA_TRANSFER
    val packet = sensors.lux?.let(BleProfile::luxPacket)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Light Exposure Tracker", style = MaterialTheme.typography.headlineSmall)
        Text("Лабораторна №2 · Віртуальний BLE-пристрій", style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(phaseLabel(state.phase), style = MaterialTheme.typography.titleLarge)
                Text("Програмна імітація · Bluetooth-адаптер не потрібний", style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = connector::scan, enabled = state.phase == BlePhase.DISCONNECTED) {
                        Text("Пошук пристроїв")
                    }
                    OutlinedButton(onClick = connector::disconnect, enabled = state.phase != BlePhase.DISCONNECTED) {
                        Text(if (busy) "Скасувати" else "Відключити")
                    }
                }
                if (state.devices.isEmpty()) Text("Натисніть пошук. Пристрій з’явиться через 2 с.")
                state.devices.forEach { device ->
                    HorizontalDivider()
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text("${device.address} · RSSI ${device.rssi} dBm (mock)", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = connector::connect, enabled = state.phase == BlePhase.DISCONNECTED) {
                        Text("Підключити")
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Обмін із хабом", style = MaterialTheme.typography.titleLarge)
                Text("Освітленість: ${sensors.lux?.let(BleProfile::decimal) ?: "—"} лк")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { packet?.let(connector::sendData) }, enabled = connected && packet != null) {
                        Text("Надіслати lux")
                    }
                    OutlinedButton(onClick = connector::readData, enabled = connected) { Text("Read статус") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { connector.sendData("SET:500") }, enabled = connected) { Text("Ціль 500 лк") }
                    OutlinedButton(
                        onClick = { connector.setNotifications(!state.notificationsEnabled) }, enabled = connected,
                    ) { Text(if (state.notificationsEnabled) "Notify: ON" else "Notify: OFF") }
                }
                Text("Read: ${state.lastRead}", style = MaterialTheme.typography.bodyMedium)
                Text("Notify: ${state.lastNotification}", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = connector::simulateInterference,
                    enabled = connected || state.phase == BlePhase.DATA_TRANSFER,
                ) { Text("Імітувати втрату зв’язку") }
            }
        }
        Text("Термінал обміну · Logcat: LightBle", style = MaterialTheme.typography.titleMedium)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.logs.isEmpty()) Text("Повідомлення з’являться після початку пошуку.")
                state.logs.asReversed().forEach { entry ->
                    Text(
                        "${entry.time} [${entry.direction}] ${entry.message}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun phaseLabel(phase: BlePhase): String = when (phase) {
    BlePhase.DISCONNECTED -> "Disconnected · Відключено"
    BlePhase.SCANNING -> "Scanning · Пошук…"
    BlePhase.CONNECTING -> "Connecting · Підключення…"
    BlePhase.CONNECTED -> "Connected · Підключено"
    BlePhase.DATA_TRANSFER -> "Data Transfer · Обмін…"
}
