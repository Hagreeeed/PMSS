package com.example.lab6.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lab6.history.SensorRecord
import com.example.lab6.history.SensorViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsScreen(history: SensorViewModel) {
    val measurements by history.measurements.collectAsStateWithLifecycle()
    val activations by history.activations.collectAsStateWithLifecycle()
    val hasError by history.hasWriteError.collectAsStateWithLifecycle()
    val interval by history.interval.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text("Аналітика освітленості", style = MaterialTheme.typography.headlineSmall)
            Text("Локальна історія · останні 50 вимірювань", style = MaterialTheme.typography.bodyMedium)
            Text(if (interval == 500L) "Запис у Room: кожні 0,5 с" else "Запис у Room: кожні 2 с")
        }
        if (hasError) item {
            Text("Не вдалося зберегти запис", color = MaterialTheme.colorScheme.error)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val latest = measurements.lastOrNull()
                    Text(
                        latest?.sensorValue?.let { "Останнє вимірювання: ${luxLabel(it)} лк" }
                            ?: "Очікуємо показників датчика",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (measurements.size < 2) {
                        Text("Для графіка потрібні два вимірювання.")
                    } else {
                        Text("Y — освітленість, лк")
                        HistoryChart(measurements)
                        Text("X — місцевий час · точок: ${measurements.size}")
                    }
                }
            }
        }
        item {
            Text("Активації лампи", style = MaterialTheme.typography.titleLarge)
            if (activations.isEmpty()) Text("Активацій ще не зафіксовано.")
        }
        items(activations, key = { it.id }) { activation ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Лампу ввімкнено · ${eventTime.format(Instant.ofEpochMilli(activation.timestamp))}")
                    Text(activation.sensorValue?.let { "Освітленість: ${luxLabel(it)} лк" }
                        ?: "Показник освітленості недоступний")
                }
            }
        }
    }
}

@Composable
private fun HistoryChart(records: List<SensorRecord>) {
    val producer = remember { CartesianChartModelProducer() }
    val points = remember(records) {
        records.mapNotNull { record -> record.sensorValue?.let { record.timestamp to it } }
            .distinctBy { it.first }
    }
    if (points.size < 2) return
    LaunchedEffect(points) {
        producer.runTransaction {
            lineSeries { series(x = points.map { it.first }, y = points.map { it.second }) }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = { _, value, _ -> axisTime.format(Instant.ofEpochMilli(value.toLong())) },
            ),
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(235.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

private fun luxLabel(lux: Float) = String.format(java.util.Locale.getDefault(), "%.1f", lux)

private val axisTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val eventTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault())
