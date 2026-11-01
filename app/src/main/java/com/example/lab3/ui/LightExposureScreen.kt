package com.example.lab3.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.lab3.sensors.Acceleration
import com.example.lab3.sensors.SensorUiState
import com.example.lab3.ui.theme.Lab1Theme
import java.util.Locale

@Composable
fun LightExposureScreen(state: SensorUiState) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Light Exposure Tracker", style = MaterialTheme.typography.headlineMedium)
            Text("Освітленість робочого місця", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.foreground) "Моніторинг відкритого екрана" else "Зчитування призупинено",
                color = MaterialTheme.colorScheme.primary,
            )
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Освітленість", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${formatValue(state.lux, 1)} лк",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(sensorStatus(state.lightAvailable, state.lightRegistered, state.foreground, state.lux != null))
                    Text("Поточне значення датчика світла", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Положення пристрою", style = MaterialTheme.typography.titleLarge)
                    Text(sensorStatus(state.accelerometerAvailable, state.accelerometerRegistered, state.foreground, state.acceleration != null))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        AxisValue("X", state.acceleration?.x)
                        AxisValue("Y", state.acceleration?.y)
                        AxisValue("Z", state.acceleration?.z)
                    }
                    Text("Прискорення, м/с²", style = MaterialTheme.typography.bodySmall)
                    TiltIndicator(state.acceleration, Modifier.align(Alignment.CenterHorizontally))
                    Text("Індикатор X / Y", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Кулька реагує на нахил. Осі пов’язані з пристроєм: +X праворуч, +Y вгору у його природній орієнтації. Прискорення включає силу тяжіння.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Text(
                "Лабораторна №2 · Сенсори з попередньої роботи\nЗчитування зупиняється, коли додаток втрачає активність.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun sensorStatus(available: Boolean, registered: Boolean, foreground: Boolean, hasValue: Boolean): String = when {
    !available -> "Сенсор недоступний на цьому пристрої"
    !foreground -> "Зчитування призупинено"
    !registered -> "Не вдалося запустити сенсор"
    !hasValue -> "Очікування першого вимірювання…"
    else -> "Сенсор активний"
}

private fun formatValue(value: Float?, decimals: Int = 2): String =
    value?.let { String.format(Locale.forLanguageTag("uk-UA"), "%.${decimals}f", it) } ?: "—"

@Composable
private fun AxisValue(axis: String, value: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(axis, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(formatValue(value), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun TiltIndicator(acceleration: Acceleration?, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val background = MaterialTheme.colorScheme.surfaceVariant
    val position = tiltPosition(acceleration?.x ?: 0f, acceleration?.y ?: 0f)
    Canvas(modifier.size(200.dp).semantics {
        contentDescription = if (acceleration == null) "Індикатор очікує дані акселерометра"
        else "Індикатор нахилу: X ${formatValue(acceleration.x)}, Y ${formatValue(acceleration.y)}"
    }) {
        val radius = size.minDimension / 2f - 8.dp.toPx()
        val ballRadius = 12.dp.toPx()
        drawCircle(background, radius, center)
        drawCircle(outline, radius, center, style = Stroke(2.dp.toPx()))
        drawLine(outline, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(outline, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
        drawCircle(outline, radius / 2f, center, style = Stroke(1.dp.toPx()))
        if (acceleration != null) {
            val travel = radius - ballRadius - 2.dp.toPx()
            drawCircle(primary, ballRadius, Offset(center.x + position.x * travel, center.y + position.y * travel))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LightExposurePreview() {
    Lab1Theme {
        LightExposureScreen(SensorUiState(
            lightAvailable = true, accelerometerAvailable = true,
            lightRegistered = true, accelerometerRegistered = true, foreground = true,
            lux = 420f, acceleration = Acceleration(2f, -3f, 9.1f),
        ))
    }
}

@Preview(showBackground = true)
@Composable
private fun MissingSensorsPreview() {
    Lab1Theme { LightExposureScreen(SensorUiState(foreground = true)) }
}
