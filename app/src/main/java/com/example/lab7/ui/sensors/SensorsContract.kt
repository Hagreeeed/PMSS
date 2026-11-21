package com.example.lab7.ui.sensors

import com.example.lab7.domain.model.SensorSnapshot

/** ЛР1: сирі значення датчиків для перегляду. */
data class SensorsState(val running: Boolean = false, val sensors: SensorSnapshot = SensorSnapshot())
