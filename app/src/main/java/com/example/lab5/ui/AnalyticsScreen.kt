package com.example.lab5.ui

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
import com.example.lab5.history.SensorRecord
import com.example.lab5.history.SensorViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import java.time.Instant
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsScreen(history: SensorViewModel) {
    val measurements by history.measurements.collectAsStateWithLifecycle()
    val activations by history.activations.collectAsStateWithLifecycle()
    val hasError by history.hasWriteError.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text("Аналітика освітленості", style = MaterialTheme.typography.headlineSmall)
            Text("Локальна історія · останні 50 вимірювань", style = MaterialTheme.typography.bodyMedium)
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
    }
    if (points.size < 2) return
    LaunchedEffect(points) {
        producer.runTransaction {
            // По X — порядковий номер вимірювання: крок завжди 1, тому масштаб осі не стрибає,
            // навіть якщо інтервал між записами плаває (2 с, 3 с або пауза після onPause).
            lineSeries { series(x = points.indices.toList(), y = points.map { it.second }) }
            extras { it[TimestampsKey] = points.map { point -> point.first } }
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(rangeProvider = StableLuxRange),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = TimeAxisFormatter,
                itemPlacer = remember { HorizontalAxis.ItemPlacer.aligned(spacing = { 10 }) },
            ),
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(235.dp),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        // Без анімації переходу: інакше при зсуві вікна кожна точка «перетікає» в сусідню.
        animationSpec = null,
        animateIn = false,
    )
}

/** Мітки часу точок графіка; передаються разом із моделлю, щоб формат осі не перестворювався. */
private val TimestampsKey = ExtraStore.Key<List<Long>>()

private val TimeAxisFormatter = CartesianValueFormatter { context, value, _ ->
    val timestamps = context.model.extraStore.getOrNull(TimestampsKey)
    timestamps?.getOrNull(value.toInt())?.let { axisTime.format(Instant.ofEpochMilli(it)) } ?: ""
}

/**
 * Вісь Y від 0 до «круглого» максимуму (1-2-5 × 10ⁿ). Межа змінюється лише тоді,
 * коли освітленість перетинає наступне кругле значення, а не при кожному новому вимірюванні.
 */
private object StableLuxRange : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 0.0

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        if (maxY <= 10.0) return 10.0
        val magnitude = 10.0.pow(floor(log10(maxY)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= maxY }
        return step * magnitude
    }
}

private fun luxLabel(lux: Float) = String.format(java.util.Locale.getDefault(), "%.1f", lux)

private val axisTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val eventTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault())
