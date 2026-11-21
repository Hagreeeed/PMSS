package com.example.lab3.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Acceleration(val x: Float, val y: Float, val z: Float)

data class SensorUiState(
    val lightAvailable: Boolean = false,
    val accelerometerAvailable: Boolean = false,
    val lightRegistered: Boolean = false,
    val accelerometerRegistered: Boolean = false,
    val foreground: Boolean = false,
    val lux: Float? = null,
    val acceleration: Acceleration? = null,
)

/** SensorManager отримує реальні або віртуальні дані; UI відображає стан. */
class WorkplaceSensorMonitor(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val light = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    var state by mutableStateOf(SensorUiState(
        lightAvailable = light != null,
        accelerometerAvailable = accelerometer != null,
    ))
        private set

    fun start() {
        if (state.foreground) return
        // Реєстрація може повернути false навіть за наявності сенсора.
        val lightRegistered = light?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: false
        val accelerometerRegistered = accelerometer?.let {
            manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: false
        state = state.copy(
            foreground = true,
            lightRegistered = lightRegistered,
            accelerometerRegistered = accelerometerRegistered,
            lux = null,
            acceleration = null,
        )
    }

    fun stop() {
        manager.unregisterListener(this)
        state = state.copy(
            foreground = false,
            lightRegistered = false,
            accelerometerRegistered = false,
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!state.foreground) return
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lux = event.values.firstOrNull() ?: return
                if (lux.isFinite() && lux >= 0f) state = state.copy(lux = lux)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (event.values.size < 3) return
                val (x, y, z) = event.values
                if (x.isFinite() && y.isFinite() && z.isFinite()) {
                    // Копіюємо числа: масив події повторно використовується Android.
                    state = state.copy(acceleration = Acceleration(x, y, z))
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
