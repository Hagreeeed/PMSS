package com.example.lab4.control

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.content.ContextCompat

/** Virtual UI is always available; physical torch is opt-in and reports actual callbacks. */
class SmartActuator(context: Context, private val onFeedback: (HardwareFeedback) -> Unit) : ActuatorPort {
    private val app = context.applicationContext
    private val cameras = app.getSystemService(CameraManager::class.java)
    private val vibrator = app.getSystemService(VibratorManager::class.java).defaultVibrator
    private var cameraId: String? = null
    private var registered = false
    private var hardware = false
    private var ownsTorch = false
    private var feedback = HardwareFeedback()
    private fun report(torch: String = feedback.torch, vibration: String = feedback.vibration) {
        feedback = HardwareFeedback(torch, vibration); onFeedback(feedback)
    }
    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (hardware && id == cameraId) report(torch = "TorchCallback: ${if (enabled) "ON" else "OFF"}")
        }
        override fun onTorchModeUnavailable(id: String) {
            if (hardware && id == cameraId) report(torch = "Ліхтарик недоступний; віртуальна лампа")
        }
    }
    fun start() {
        if (registered) return
        try {
            cameraId = cameras.cameraIdList.firstOrNull { cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            cameras.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            registered = true
        } catch (error: Exception) { report(torch = "Камера недоступна; віртуальна лампа") }
    }
    fun hardwareEnabled(enabled: Boolean, lampOn: Boolean) {
        if (!enabled && ownsTorch) toggleFlashlight(false)
        hardware = enabled
        toggleFlashlight(lampOn)
    }
    override fun toggleFlashlight(status: Boolean) {
        if (!hardware) { report(torch = "Віртуальна лампа: ${if (status) "ON" else "OFF"}"); return }
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            report(torch = "Немає CAMERA; віртуальна лампа"); return
        }
        val id = cameraId
        if (id == null) { report(torch = "Спалаху немає; віртуальна лампа"); return }
        try {
            cameras.setTorchMode(id, status)
            ownsTorch = status
            report(torch = "Запит ${if (status) "ON" else "OFF"}; очікування TorchCallback")
        } catch (error: Exception) { report(torch = "${error.javaClass.simpleName}; віртуальна лампа") }
    }
    override fun triggerAlarm() {
        try {
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                report(vibration = "VibratorManager: імпульс 180 мс надіслано")
            } else report(vibration = "Вібромотор відсутній; подія показана в UI")
        } catch (error: Exception) { report(vibration = "Вібрація недоступна: ${error.javaClass.simpleName}") }
    }
    override fun stop() {
        if (ownsTorch) toggleFlashlight(false)
        else report(torch = "Виконавчі пристрої OFF")
        vibrator.cancel()
        if (registered) { cameras.unregisterTorchCallback(callback); registered = false }
    }
}
