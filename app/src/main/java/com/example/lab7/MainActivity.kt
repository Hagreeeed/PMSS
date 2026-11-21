package com.example.lab7

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.lab7.ui.AppRoot
import com.example.lab7.ui.theme.LightTrackerTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as LightTrackerApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LightTrackerTheme { AppRoot() } }
    }

    // Activity лише передає події життєвого циклу. Коли екран не видно,
    // датчики, inference, запис і опитування зупиняються.
    override fun onStart() {
        super.onStart()
        container.pipeline.start()
    }

    override fun onStop() {
        // Поворот екрана перестворює Activity: моніторинг і BLE-з'єднання при цьому не перериваються.
        if (!isChangingConfigurations) {
            container.pipeline.stop()
            container.ble.disconnect()
        }
        super.onStop()
    }
}
