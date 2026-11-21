package com.example.lab6

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import com.example.lab6.cloud.CloudViewModel
import com.example.lab6.control.ControlViewModel
import com.example.lab6.history.SensorViewModel
import com.example.lab6.ai.LightActivity
import com.example.lab6.ai.LightAiViewModel
import com.example.lab6.ble.MockBleConnector
import com.example.lab6.sensors.WorkplaceSensorMonitor
import com.example.lab6.ui.LabApp
import com.example.lab6.ui.theme.LightTrackerTheme

class MainActivity : ComponentActivity() {
    private lateinit var sensorMonitor: WorkplaceSensorMonitor
    private lateinit var bleConnector: MockBleConnector
    private lateinit var cloud: CloudViewModel
    private lateinit var control: ControlViewModel
    private lateinit var history: SensorViewModel
    private lateinit var ai: LightAiViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorMonitor = WorkplaceSensorMonitor(applicationContext)
        bleConnector = MockBleConnector(lifecycleScope, logger = { Log.i("LightBle", it) })
        cloud = ViewModelProvider(this)[CloudViewModel::class.java]
        control = ViewModelProvider(this)[ControlViewModel::class.java]
        history = ViewModelProvider(this)[SensorViewModel::class.java]
        ai = ViewModelProvider(this)[LightAiViewModel::class.java]
        control.observeActivations(history::onActivation)
        lifecycleScope.launch {
            snapshotFlow { sensorMonitor.state }.collect(cloud::onSensorsChanged)
        }
        lifecycleScope.launch {
            snapshotFlow { sensorMonitor.state }.collect {
                val lux = if (it.foreground) it.lux else null
                history.onLux(lux)
                control.onLux(lux)
                ai.onLux(lux)
            }
        }
        enableEdgeToEdge()
        setContent {
            LightTrackerTheme {
                LabApp(sensorMonitor.state, bleConnector, cloud, control, history, ai)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorMonitor.start()
        cloud.start()
        control.start()
        history.start()
        ai.start { activity ->
            history.setFastSampling(activity == LightActivity.CHANGING || activity == LightActivity.SHADOW)
            control.onAnomaly(activity == LightActivity.SHADOW)
        }
    }

    override fun onPause() {
        // Відписуємося від обох сенсорів, коли екран втрачає активність.
        cloud.stop()
        ai.stop()
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
