package com.example.lab7.ui.rules

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab7.domain.model.RuleAction
import com.example.lab7.domain.model.RuleMetric
import com.example.lab7.domain.model.RuleOperator
import com.example.lab7.ui.common.Amber
import com.example.lab7.ui.common.Grey
import com.example.lab7.ui.common.StatusDot

@Composable
fun RulesScreen(model: RulesViewModel = viewModel(factory = RulesViewModel.Factory)) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(model) {
        model.eventFlow.collect { if (it is RulesEvent.Message) Toast.makeText(context, it.text, Toast.LENGTH_SHORT).show() }
    }
    RulesContent(state, model::onAction)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesContent(state: RulesState, onAction: (RulesAction) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Smart-сценарії", style = MaterialTheme.typography.headlineSmall)
        Text("Дія виконується один раз, коли умова стає істинною.", style = MaterialTheme.typography.bodySmall)
        state.rules.forEach { rule ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusDot(if (rule.id in state.activeIds) Amber else Grey)
                    Text(rule.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = rule.enabled, onCheckedChange = { onAction(RulesAction.Toggle(rule)) })
                    TextButton(onClick = { onAction(RulesAction.Delete(rule.id)) }) { Text("✕") }
                }
            }
        }
        if (state.rules.isEmpty()) Text("Правил ще немає")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Нове правило", style = MaterialTheme.typography.titleMedium)
                Text("Якщо", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleMetric.entries.forEach { metric ->
                        FilterChip(state.metric == metric, { onAction(RulesAction.SelectMetric(metric)) }, { Text(metric.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleOperator.entries.forEach { op ->
                        FilterChip(state.operator == op, { onAction(RulesAction.SelectOperator(op)) }, { Text(op.symbol) })
                    }
                    OutlinedTextField(
                        value = state.threshold,
                        onValueChange = { onAction(RulesAction.ThresholdChanged(it)) },
                        label = { Text("Поріг, ${state.metric.unit}") },
                        isError = state.thresholdError != null,
                        supportingText = state.thresholdError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("то", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RuleAction.entries.forEach { action ->
                        FilterChip(state.action == action, { onAction(RulesAction.SelectAction(action)) }, { Text(action.label) })
                    }
                }
                Button(onClick = { onAction(RulesAction.Add) }) { Text("Додати правило") }
            }
        }
    }
}
