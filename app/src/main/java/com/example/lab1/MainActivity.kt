package com.example.lab1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.lab1.sensors.WorkplaceSensorMonitor
import com.example.lab1.ui.LightExposureScreen
import com.example.lab1.ui.theme.Lab1Theme

class MainActivity : ComponentActivity() {
    private lateinit var sensorMonitor: WorkplaceSensorMonitor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorMonitor = WorkplaceSensorMonitor(applicationContext)
        enableEdgeToEdge()
        setContent {
            Lab1Theme {
                LightExposureScreen(sensorMonitor.state)
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
        super.onPause()
    }
}
