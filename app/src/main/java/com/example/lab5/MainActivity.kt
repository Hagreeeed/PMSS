package com.example.lab5

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import com.example.lab5.cloud.CloudViewModel
import com.example.lab5.control.ControlViewModel
import com.example.lab5.history.SensorViewModel
import com.example.lab5.ble.MockBleConnector
import com.example.lab5.sensors.WorkplaceSensorMonitor
import com.example.lab5.ui.LabApp
import com.example.lab5.ui.theme.LightTrackerTheme

class MainActivity : ComponentActivity() {
    private lateinit var sensorMonitor: WorkplaceSensorMonitor
    private lateinit var bleConnector: MockBleConnector
    private lateinit var cloud: CloudViewModel
    private lateinit var control: ControlViewModel
    private lateinit var history: SensorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorMonitor = WorkplaceSensorMonitor(applicationContext)
        bleConnector = MockBleConnector(lifecycleScope, logger = { Log.i("LightBle", it) })
        cloud = ViewModelProvider(this)[CloudViewModel::class.java]
        control = ViewModelProvider(this)[ControlViewModel::class.java]
        history = ViewModelProvider(this)[SensorViewModel::class.java]
        control.observeActivations(history::onActivation)
        lifecycleScope.launch {
            snapshotFlow { sensorMonitor.state }.collect(cloud::onSensorsChanged)
        }
        lifecycleScope.launch {
            snapshotFlow { sensorMonitor.state }.collect {
                val lux = if (it.foreground) it.lux else null
                history.onLux(lux)
                control.onLux(lux)
            }
        }
        enableEdgeToEdge()
        setContent {
            LightTrackerTheme {
                LabApp(sensorMonitor.state, bleConnector, cloud, control, history)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorMonitor.start()
        cloud.start()
        control.start()
        history.start()
    }

    override fun onPause() {
        // Відписуємося від обох сенсорів, коли екран втрачає активність.
        cloud.stop()
        history.stop()
        control.stop()
        sensorMonitor.stop()
        // The mock has no background service: cancel scanning/transfers when leaving the app.
        bleConnector.disconnect()
        super.onPause()
    }

    override fun onDestroy() {
        control.observeActivations(null)
        super.onDestroy()
    }
}
