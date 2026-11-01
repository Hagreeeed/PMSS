package com.example.lab2

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import com.example.lab2.cloud.CloudViewModel
import com.example.lab2.ble.MockBleConnector
import com.example.lab2.sensors.WorkplaceSensorMonitor
import com.example.lab2.ui.LabApp
import com.example.lab2.ui.theme.Lab1Theme

class MainActivity : ComponentActivity() {
    private lateinit var sensorMonitor: WorkplaceSensorMonitor
    private lateinit var bleConnector: MockBleConnector
    private lateinit var cloud: CloudViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorMonitor = WorkplaceSensorMonitor(applicationContext)
        bleConnector = MockBleConnector(lifecycleScope, logger = { Log.i("LightBle", it) })
        cloud = ViewModelProvider(this)[CloudViewModel::class.java]
        lifecycleScope.launch {
            snapshotFlow { sensorMonitor.state }.collect(cloud::onSensorsChanged)
        }
        enableEdgeToEdge()
        setContent {
            Lab1Theme {
                LabApp(sensorMonitor.state, bleConnector, cloud)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorMonitor.start()
        cloud.start()
    }

    override fun onPause() {
        // Відписуємося від обох сенсорів, коли екран втрачає активність.
        cloud.stop()
        sensorMonitor.stop()
        // The mock has no background service: cancel scanning/transfers when leaving the app.
        bleConnector.disconnect()
        super.onPause()
    }
}
