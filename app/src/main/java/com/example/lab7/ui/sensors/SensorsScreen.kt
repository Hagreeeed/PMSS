package com.example.lab7.ui.sensors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab7.domain.model.Acceleration
import com.example.lab7.ui.common.factory
import com.example.lab7.ui.common.lux
import java.util.Locale

@Composable
fun SensorsScreen(model: SensorsViewModel = viewModel(factory = SensorsViewModel.Factory)) {
    val state by model.state.collectAsStateWithLifecycle()
    val sensors = state.sensors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Датчики", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Освітленість (без фільтра)", style = MaterialTheme.typography.titleMedium)
                Text("${lux(sensors.lux)} лк", style = MaterialTheme.typography.displaySmall)
                Text(availability(sensors.lightAvailable, state.running), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Акселерометр, м/с²", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Axis("X", sensors.acceleration?.x)
                    Axis("Y", sensors.acceleration?.y)
                    Axis("Z", sensors.acceleration?.z)
                }
                Tilt(sensors.acceleration, Modifier.align(Alignment.CenterHorizontally))
                Text(availability(sensors.accelerometerAvailable, state.running), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun availability(available: Boolean, running: Boolean) = when {
    !available -> "Датчика немає на пристрої"
    !running -> "Зчитування зупинено"
    else -> "Датчик активний"
}

@Composable
private fun Axis(name: String, value: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, color = MaterialTheme.colorScheme.primary)
        Text(value?.let { String.format(Locale.US, "%.2f", it) } ?: "—", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Tilt(acceleration: Acceleration?, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val position = tiltPosition(acceleration?.x ?: 0f, acceleration?.y ?: 0f)
    Canvas(modifier.size(180.dp)) {
        val radius = size.minDimension / 2f - 8.dp.toPx()
        val ball = 12.dp.toPx()
        drawCircle(outline, radius, center, style = Stroke(2.dp.toPx()))
        drawLine(outline, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y))
        drawLine(outline, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius))
        if (acceleration != null) {
            val travel = radius - ball
            drawCircle(primary, ball, Offset(center.x + position.x * travel, center.y + position.y * travel))
        }
    }
}
