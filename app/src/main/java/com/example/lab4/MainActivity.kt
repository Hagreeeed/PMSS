package com.example.lab4

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import com.example.lab4.cloud.CloudViewModel
import com.example.lab4.control.ControlViewModel
import com.example.lab4.ble.MockBleConnector
import com.example.lab4.sensors.WorkplaceSensorMonitor
import com.example.lab4.ui.LabApp
import com.example.lab4.ui.theme.Lab1Theme

class MainActivity : ComponentActivity() {
    private lateinit var sensorMonitor: WorkplaceSensorMonitor
    private lateinit var bleConnector: MockBleConnector
    private lateinit var cloud: CloudViewModel
    private lateinit var control: ControlViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorMonitor = WorkplaceSensorMonitor(applicationContext)
        bleConnector = MockBleConnector(lifecycleScope, logger = { Log.i("LightBle", it) })
        cloud = ViewModelProvider(this)[CloudViewModel::class.java]
        control = ViewModelProvider(this)[ControlViewModel::class.java]
        lifecycleScope.launch {
            snapshotFlow { sensorMonitor.state }.collect(cloud::onSensorsChanged)
        }
        lifecycleScope.launch {
            snapshotFlow { sensorMonitor.state }.collect { control.onLux(if (it.foreground) it.lux else null) }
        }
        enableEdgeToEdge()
        setContent {
            Lab1Theme {
                LabApp(sensorMonitor.state, bleConnector, cloud, control)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorMonitor.start()
        cloud.start()
        control.start()
    }

    override fun onPause() {
        // Відписуємося від обох сенсорів, коли екран втрачає активність.
        cloud.stop()
        control.stop()
        sensorMonitor.stop()
        // The mock has no background service: cancel scanning/transfers when leaving the app.
        bleConnector.disconnect()
        super.onPause()
    }
}
