package com.example.lab7.data.device

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.VibrationEffect
import android.os.VibratorManager
import com.example.lab7.domain.Actuator

/**
 * Актуатори ЛР4: вібрація через VibratorManager і ліхтарик через CameraManager.
 * Якщо спалаху немає (як в емуляторі), лампа показується тільки на екрані.
 */
class AndroidActuator(context: Context, private val logger: (String) -> Unit = {}) : Actuator {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val cameras = context.getSystemService(CameraManager::class.java)
    private val hasFlash = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    private val torchId: String? by lazy {
        if (!hasFlash) return@lazy null
        runCatching {
            cameras.cameraIdList.firstOrNull {
                cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }
    private var torchOn = false

    override fun vibrate() {
        if (!vibrator.hasVibrator()) {
            logger("Вібромотора немає")
            return
        }
        vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun lamp(on: Boolean) {
        val id = torchId ?: return
        try {
            cameras.setTorchMode(id, on)
            torchOn = on
        } catch (error: Exception) {
            logger("Ліхтарик недоступний: ${error.javaClass.simpleName}")
        }
    }

    override fun release() {
        if (torchOn) lamp(false)
        vibrator.cancel()
    }
}
