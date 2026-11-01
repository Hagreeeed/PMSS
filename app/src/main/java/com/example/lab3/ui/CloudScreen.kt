package com.example.lab3.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.lab3.cloud.CloudState
import com.example.lab3.cloud.SyncPhase
import com.example.lab3.sensors.SensorUiState

@Composable
fun CloudScreen(state: CloudState, sensors: SensorUiState) {
    val color = when (state.phase) {
        SyncPhase.SYNCED -> Color(0xFF237A38)
        SyncPhase.PENDING -> Color(0xFFB3261E)
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Light Exposure Tracker", style = MaterialTheme.typography.headlineSmall)
        Text("ЛР3 · REST + Retrofit + Gson", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(18.dp).background(color, CircleShape))
                    Text("Хмарна синхронізація", style = MaterialTheme.typography.titleLarge)
                }
                Text(state.phase.name, color = color, style = MaterialTheme.typography.titleMedium)
                Text(state.message)
                if (state.phase == SyncPhase.SENDING) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Pending: ${state.pending} · Надіслано: ${state.sent}")
                Text("HTTP: ${state.httpCode ?: "—"} · Інтервал: 5 с")
                Text("Освітленість: ${sensors.lux ?: "—"} лк")
                Text("X: ${sensors.acceleration?.x ?: "—"}  Y: ${sensors.acceleration?.y ?: "—"}  Z: ${sensors.acceleration?.z ?: "—"}")
            }
        }
        Text("POST https://jsonplaceholder.typicode.com/posts", style = MaterialTheme.typography.bodySmall)
        Text("Демо API: повертає дані, але не зберігає їх на сервері. Pending зберігається локально.", style = MaterialTheme.typography.bodyMedium)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Останній запит · JSON", style = MaterialTheme.typography.titleMedium)
                Text(state.lastRequest, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("Відповідь API · JSON", style = MaterialTheme.typography.titleMedium)
                Text(state.lastResponse, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("Журнал · LightCloud / LightHttp", style = MaterialTheme.typography.titleMedium)
                state.logs.asReversed().forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
