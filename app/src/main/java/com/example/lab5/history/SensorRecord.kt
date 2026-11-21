package com.example.lab5.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_records", indices = [Index("actionTriggered", "timestamp", "id")])
data class SensorRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val sensorValue: Float?,
    val actionTriggered: Boolean,
)
