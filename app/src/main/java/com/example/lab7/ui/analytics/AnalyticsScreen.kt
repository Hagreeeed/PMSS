package com.example.lab7.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab7.domain.model.Reading
import com.example.lab7.ui.common.factory
import com.example.lab7.ui.common.lux
import com.example.lab7.ui.common.time
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
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun AnalyticsScreen(model: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory)) {
    val state by model.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            Text("Аналітика освітленості", style = MaterialTheme.typography.headlineSmall)
            Text("Останні 50 вимірювань з Room (після фільтра)", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (state.measurements.size < 2) Text("Для графіка потрібні два вимірювання")
                    else HistoryChart(state.measurements)
                }
            }
        }
        item { Text("Події", style = MaterialTheme.typography.titleLarge) }
        if (state.events.isEmpty()) item { Text("Подій ще немає") }
        items(state.events, key = { it.id }) { event ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(event.event.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Text("${time(event.timestamp)} · ${lux(event.lux)} лк", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun HistoryChart(records: List<Reading>) {
    val producer = remember { CartesianChartModelProducer() }
    val points = remember(records) { records.mapNotNull { r -> r.lux?.let { r.timestamp to it } } }
    if (points.size < 2) return
    LaunchedEffect(points) {
        producer.runTransaction {
            // x = номер точки, щоб крок осі не залежав від інтервалу між записами
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
        // без анімації, інакше графік смикається при кожному новому записі
        animationSpec = null,
        animateIn = false,
    )
}

private val TimestampsKey = ExtraStore.Key<List<Long>>()

private val TimeAxisFormatter = CartesianValueFormatter { context, value, _ ->
    context.model.extraStore.getOrNull(TimestampsKey)?.getOrNull(value.toInt())?.let(::time) ?: ""
}

// Y від 0 до круглого значення (10, 20, 50, 100...), щоб вісь не мінялась на кожній точці
private object StableLuxRange : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) = 0.0

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        if (maxY <= 10.0) return 10.0
        val magnitude = 10.0.pow(floor(log10(maxY)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).first { it * magnitude >= maxY }
        return step * magnitude
    }
}
