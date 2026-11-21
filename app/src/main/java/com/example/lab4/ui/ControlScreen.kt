package com.example.lab4.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lab4.BuildConfig
import com.example.lab4.control.*

@Composable
fun ControlScreen(model: ControlViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.hardware(it)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Light Exposure Tracker", style = MaterialTheme.typography.headlineSmall)
        Text("ЛР4 · Керування виконавчими пристроями")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(36.dp).background(if (state.lampOn) Color(0xFFFFC107) else Color(0xFF78909C), CircleShape))
                    Text("Лампа: ${if (state.lampOn) "ON" else "OFF"}", style = MaterialTheme.typography.titleLarge)
                }
                Text("Режим: ${state.mode} · ${state.lux?.let { "%.1f".format(it) } ?: "—"} лк")
                Text(state.reason)
                Text("Пороги: ON < 300 лк · OFF ≥ 350 лк")
                Text("Активацій / віброімпульсів: ${state.alarmCount}")
                Text(state.feedback.torch, style = MaterialTheme.typography.bodySmall)
                Text(state.feedback.vibration, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ControlMode.entries.forEach { mode ->
                FilterChip(selected = state.mode == mode, onClick = { model.select(mode) }, label = { Text(mode.name) })
            }
        }
        if (state.mode == ControlMode.MANUAL) {
            Text("Manual Control", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { model.manual(true) }) { Text("Увімкнути") }
                OutlinedButton(onClick = { model.manual(false) }) { Text("Вимкнути") }
            }
            Text("Сенсор і REST не змінюють ручний стан.")
        }
        if (state.mode == ControlMode.REMOTE) {
            Text("Cloud-to-Device · REST polling 3 с", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (BuildConfig.DEBUG) OutlinedButton(onClick = { model.useDemo(true) }) { Text("Demo hub") }
                OutlinedButton(onClick = { model.useDemo(false) }) { Text("Public API") }
            }
            Text(model.endpoint, style = MaterialTheme.typography.bodySmall)
            Text(state.remoteStatus)
            Text(state.lastJson, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text("Demo: запустіть demo-hub/server.py та adb reverse tcp:8787 tcp:8787. Команду змінюють у браузері ПК. Public API повертає незмінний OFF.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Апаратний ліхтарик", modifier = Modifier.weight(1f))
            Switch(checked = state.hardwareRequested, onCheckedChange = {
                if (it) permission.launch(Manifest.permission.CAMERA)
                else model.hardware(false)
            })
        }
        Text("Без спалаху або дозволу працює віртуальна лампа. Статус фізичного ліхтарика надходить через TorchCallback. У фоні всі виконавчі пристрої вимикаються.", style = MaterialTheme.typography.bodySmall)
        Text("Журнал · LightControl", style = MaterialTheme.typography.titleMedium)
        state.logs.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
    }
}
