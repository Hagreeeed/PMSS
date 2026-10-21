package com.example.lab2

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.lab2.ble.MockBleConnector
import com.example.lab2.sensors.WorkplaceSensorMonitor
import com.example.lab2.ui.Lab2App
import com.example.lab2.ui.theme.Lab1Theme

class MainActivity : ComponentActivity() {
    private lateinit var sensorMonitor: WorkplaceSensorMonitor
    private lateinit var bleConnector: MockBleConnector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorMonitor = WorkplaceSensorMonitor(applicationContext)
        bleConnector = MockBleConnector(lifecycleScope, logger = { Log.i("LightBle", it) })
        enableEdgeToEdge()
        setContent {
            Lab1Theme {
                Lab2App(sensorMonitor.state, bleConnector)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorMonitor.start()
    }

    override fun onPause() {
        // Відписуємося від обох сенсорів, коли екран втрачає активність.
        sensorMonitor.stop()
        // The mock has no background service: cancel scanning/transfers when leaving the app.
        bleConnector.disconnect()
        super.onPause()
    }
}
