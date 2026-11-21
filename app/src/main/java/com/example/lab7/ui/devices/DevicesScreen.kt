package com.example.lab7.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab7.data.ble.BlePhase
import com.example.lab7.domain.model.ControlMode
import com.example.lab7.ui.common.time

@Composable
fun DevicesScreen(model: DevicesViewModel = viewModel(factory = DevicesViewModel.Factory)) {
    val state by model.state.collectAsStateWithLifecycle()
    DevicesContent(state, model::onAction)
}

@Composable
private fun DevicesContent(state: DevicesState, onAction: (DevicesAction) -> Unit) {
    val ble = state.ble
    val connected = ble.phase == BlePhase.CONNECTED
    val busy = ble.phase == BlePhase.SCANNING || ble.phase == BlePhase.CONNECTING || ble.phase == BlePhase.DATA_TRANSFER
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mock BLE-хаб · ${ble.phase}", style = MaterialTheme.typography.titleLarge)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAction(DevicesAction.Scan) }, enabled = ble.phase == BlePhase.DISCONNECTED) { Text("Пошук") }
                    Button(
                        onClick = { onAction(DevicesAction.Connect) },
                        enabled = ble.phase == BlePhase.DISCONNECTED && ble.devices.isNotEmpty(),
                    ) { Text("Підключити") }
                    OutlinedButton(onClick = { onAction(DevicesAction.Disconnect) }, enabled = ble.phase != BlePhase.DISCONNECTED) { Text("Відкл.") }
                }
                ble.devices.forEach { Text("${it.name} · ${it.address} · RSSI ${it.rssi} dBm (mock)", style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAction(DevicesAction.Read) }, enabled = connected) { Text("Read") }
                    OutlinedButton(onClick = { onAction(DevicesAction.ToggleNotify) }, enabled = connected) {
                        Text(if (ble.notificationsEnabled) "Notify ON" else "Notify OFF")
                    }
                    OutlinedButton(onClick = { onAction(DevicesAction.Interference) }, enabled = connected) { Text("Перешкода") }
                }
                Text("Read: ${ble.lastRead}", style = MaterialTheme.typography.bodySmall)
                Text("Notify: ${ble.lastNotification}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Термінал BLE · Logcat: LightBle", style = MaterialTheme.typography.titleMedium)
        ble.logs.asReversed().take(12).forEach {
            Text("${it.time} [${it.direction}] ${it.message}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Хмара (REST)", style = MaterialTheme.typography.titleLarge)
                Text("${state.hubUrl}readings", style = MaterialTheme.typography.bodySmall)
                Text("Мережа: ${if (state.cloud.online) "є" else "немає"} · ${state.cloud.phase} · Pending: ${state.cloud.pending}")
                Text(state.cloud.message + (state.cloud.lastSyncAt?.let { " · ${time(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onAction(DevicesAction.SyncNow) }) { Text("Синхронізувати зараз") }
                if (state.mode == ControlMode.REMOTE) Text("Команди хаба: ${state.remoteStatus}")
            }
        }
        Text("Журнал системи · Logcat: LightSystem", style = MaterialTheme.typography.titleMedium)
        state.log.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
    }
}
