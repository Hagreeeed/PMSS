package com.example.lab7.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lab7.ui.analytics.AnalyticsScreen
import com.example.lab7.ui.dashboard.DashboardScreen
import com.example.lab7.ui.devices.DevicesScreen
import com.example.lab7.ui.rules.RulesScreen
import com.example.lab7.ui.sensors.SensorsScreen

private enum class Destination(val label: String) {
    DASHBOARD("Панель"), ANALYTICS("Історія"), RULES("Правила"), DEVICES("Зв'язок"), SENSORS("Датчики")
}

@Composable
fun AppRoot() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = selected) {
                Destination.entries.forEach { destination ->
                    Tab(selected = selected == destination.ordinal, onClick = { selected = destination.ordinal }) {
                        Text(destination.label, Modifier.padding(vertical = 14.dp), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            when (Destination.entries[selected]) {
                Destination.DASHBOARD -> DashboardScreen()
                Destination.ANALYTICS -> AnalyticsScreen()
                Destination.RULES -> RulesScreen()
                Destination.DEVICES -> DevicesScreen()
                Destination.SENSORS -> SensorsScreen()
            }
        }
    }
}
