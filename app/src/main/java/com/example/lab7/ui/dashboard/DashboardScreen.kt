package com.example.lab7.ui.dashboard

import android.widget.Toast
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab7.data.ble.BlePhase
import com.example.lab7.domain.model.ControlMode
import com.example.lab7.domain.model.Reading
import com.example.lab7.domain.model.SyncPhase
import com.example.lab7.ui.common.Amber
import com.example.lab7.ui.common.Green
import com.example.lab7.ui.common.Grey
import com.example.lab7.ui.common.Red
import com.example.lab7.ui.common.StatusDot
import com.example.lab7.ui.common.lux
import com.example.lab7.ui.common.percent
import com.example.lab7.ui.common.time

@Composable
fun DashboardScreen(model: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(model) {
        model.eventFlow.collect { event ->
            when (event) {
                is DashboardEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
            }
        }
    }
    DashboardContent(state, model::onAction)
}

@Composable
private fun DashboardContent(state: DashboardState, onAction: (DashboardAction) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StateCard(state)
        state.storageError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LampCard(state, onAction)
        ConnectionsCard(state, onAction)
        LatestCard(state.latest)
    }
}

@Composable
private fun StateCard(state: DashboardState) {
    val colors = if (state.critical) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    else CardDefaults.cardColors()
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (state.critical) "КРИТИЧНИЙ СТАН" else "Стан у нормі", style = MaterialTheme.typography.labelLarge)
            val prediction = state.prediction
            when {
                !state.running -> Text("Моніторинг зупинено", style = MaterialTheme.typography.titleLarge)
                state.modelError -> Text("Модель не завантажилась", color = MaterialTheme.colorScheme.error)
                prediction == null -> {
                    Text("AI: збираємо вікно ${state.windowFill} / ${state.windowSize}")
                    LinearProgressIndicator(
                        progress = { state.windowFill / state.windowSize.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> Text(
                    "${prediction.lightClass.label} · ${percent(prediction.confidence)}",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text("${lux(state.filteredLux)} лк", style = MaterialTheme.typography.displaySmall)
            Text("Датчик: ${lux(state.rawLux)} лк · після фільтра: ${lux(state.filteredLux)} лк", style = MaterialTheme.typography.bodySmall)
            Text(
                "Запис у Room кожні ${state.recordIntervalMs / 1000.0} с · відфільтровано викидів: ${state.filteredSpikes}",
                style = MaterialTheme.typography.bodySmall,
            )
            state.activeRules.forEach { Text("Активне правило: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun LampCard(state: DashboardState, onAction: (DashboardAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(if (state.lampOn) Amber else Grey, 28.dp)
                Column {
                    Text("Лампа: ${if (state.lampOn) "ON" else "OFF"}", style = MaterialTheme.typography.titleLarge)
                    Text(state.lampReason, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ControlMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { onAction(DashboardAction.SetMode(mode)) },
                        label = { Text(mode.name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(DashboardAction.Lamp(true)) }, enabled = state.mode == ControlMode.MANUAL) { Text("Увімк.") }
                OutlinedButton(onClick = { onAction(DashboardAction.Lamp(false)) }, enabled = state.mode == ControlMode.MANUAL) { Text("Вимк.") }
                OutlinedButton(onClick = { onAction(DashboardAction.TestVibration) }) { Text("Вібрація") }
            }
        }
    }
}

@Composable
private fun ConnectionsCard(state: DashboardState, onAction: (DashboardAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val cloudColor = when (state.cloud.phase) {
                SyncPhase.SYNCED, SyncPhase.IDLE -> Green
                SyncPhase.SYNCING -> Amber
                SyncPhase.OFFLINE, SyncPhase.ERROR -> Red
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(cloudColor)
                Text("Хмара: ${state.cloud.phase} · Pending: ${state.cloud.pending}", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                state.cloud.message + (state.cloud.lastSyncAt?.let { " · остання синхронізація ${time(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            val connected = state.blePhase == BlePhase.CONNECTED || state.blePhase == BlePhase.DATA_TRANSFER
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot(if (connected) Green else if (state.blePhase == BlePhase.DISCONNECTED) Red else Amber)
                Text("BLE-хаб: ${state.blePhase}", style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(DashboardAction.ConnectHub) }, enabled = state.blePhase == BlePhase.DISCONNECTED) {
                    Text("Підключити хаб")
                }
                OutlinedButton(onClick = { onAction(DashboardAction.DisconnectHub) }, enabled = state.blePhase != BlePhase.DISCONNECTED) {
                    Text("Відключити")
                }
            }
        }
    }
}

@Composable
private fun LatestCard(latest: List<Reading>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Останні 5 записів у БД", style = MaterialTheme.typography.titleMedium)
            if (latest.isEmpty()) Text("Записів ще немає")
            latest.forEach { reading ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(if (reading.synced) Green else Grey, 10.dp)
                    Text(
                        "${time(reading.timestamp)}  ${lux(reading.lux)} лк  " +
                            (reading.event ?: reading.lightClass?.label ?: "—"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text("Зелена точка: запис підтверджено хмарою", style = MaterialTheme.typography.labelSmall)
        }
    }
}
