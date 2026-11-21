package com.example.lab7.data.device

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.lab7.domain.SensorSource
import com.example.lab7.domain.model.Acceleration
import com.example.lab7.domain.model.SensorSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Датчик освітленості й акселерометр (ЛР1) як Flow. */
class AndroidSensorSource(context: Context) : SensorSource {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val light: Sensor? = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val accelerometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override fun readings(): Flow<SensorSnapshot> = callbackFlow {
        var snapshot = SensorSnapshot(lightAvailable = light != null, accelerometerAvailable = accelerometer != null)
        trySend(snapshot)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                snapshot = when (event.sensor.type) {
                    Sensor.TYPE_LIGHT -> {
                        val lux = event.values.firstOrNull()?.takeIf { it.isFinite() && it >= 0f } ?: return
                        snapshot.copy(lux = lux)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        if (event.values.size < 3) return
                        // Значення копіюються: Android повторно використовує масив події.
                        val (x, y, z) = event.values
                        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
                        snapshot.copy(acceleration = Acceleration(x, y, z))
                    }
                    else -> return
                }
                trySend(snapshot)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        light?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometer?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
