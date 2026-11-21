package com.example.lab6.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lab6.ai.LightActivity
import com.example.lab6.ai.LightAiViewModel
import com.example.lab6.control.ControlMode
import com.example.lab6.control.ControlViewModel
import com.example.lab6.history.SensorViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun AiInsightsScreen(ai: LightAiViewModel, history: SensorViewModel, control: ControlViewModel, lux: Float?) {
    val state by ai.state.collectAsStateWithLifecycle()
    val interval by history.interval.collectAsStateWithLifecycle()
    val lamp by control.state.collectAsStateWithLifecycle()
    val prediction = state.prediction
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AI Insights", style = MaterialTheme.typography.headlineMedium)
        Text("Класифікація освітленості на пристрої")
        Text(lux?.let { "Поточне освітлення: ${it.roundToInt()} лк" } ?: "Очікуємо датчик світла")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                when {
                    state.modelError -> Text("Не вдалося запустити локальну модель", color = MaterialTheme.colorScheme.error)
                    prediction == null -> {
                        Text("Збираємо вікно: ${state.sampleCount} / 20")
                        LinearProgressIndicator(progress = { state.sampleCount / 20f }, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        Text(
                            "${prediction.activity.label} · ${percent(prediction.confidence)}%",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (prediction.accepted == null) Text("Низька впевненість · дії не запускаються")
                        LightActivity.entries.forEach { activity ->
                            val probability = prediction.probabilities[activity.ordinal]
                            Text("${activity.label} — ${percent(probability)}%")
                            LinearProgressIndicator(progress = { probability }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Реакція системи", style = MaterialTheme.typography.titleMedium)
                Text(if (interval == 500L) "Room: кожні 0,5 с" else "Room: кожні 2 с")
                Text("Лампа: ${if (lamp.lampOn) "ON" else "OFF"} · ${lamp.mode.name}")
                if (lamp.mode == ControlMode.AUTO) Text(lamp.reason)
                state.lastShadowAt?.let {
                    Text("Останнє затемнення: ${shadowTime.format(Instant.ofEpochMilli(it))}")
                }
            }
        }
    }
}

private fun percent(value: Float) = (100 * value).roundToInt()
private val shadowTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
