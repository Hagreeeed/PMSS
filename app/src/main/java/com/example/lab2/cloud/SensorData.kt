package com.example.lab2.cloud

import java.util.UUID

/** Lux is the primary value; x/y/z carry the simultaneous accelerometer reading in m/s². */
data class SensorData(
    val deviceId: String,
    val timestamp: Long,
    val value: Float,
    val type: String = "light",
    val x: Float? = null,
    val y: Float? = null,
    val z: Float? = null,
    val sampleId: String = UUID.randomUUID().toString(),
)

enum class SyncPhase { WAITING, PENDING, SENDING, SYNCED }

data class CloudState(
    val phase: SyncPhase = SyncPhase.WAITING,
    val pending: Int = 0,
    val sent: Int = 0,
    val message: String = "Очікування першого вимірювання",
    val lastRequest: String = "—",
    val lastResponse: String = "—",
    val httpCode: Int? = null,
    val logs: List<String> = emptyList(),
)
