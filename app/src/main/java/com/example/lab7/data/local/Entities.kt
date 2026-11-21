package com.example.lab7.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "readings",
    indices = [Index(value = ["sampleId"], unique = true), Index("synced", "id"), Index("isEvent", "timestamp")],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Ідентифікатор для хмари: повторна відправка того самого запису не створює дубль. */
    val sampleId: String,
    val timestamp: Long,
    val rawLux: Float?,
    val lux: Float?,
    val lightClass: String?,
    val confidence: Float?,
    val event: String?,
    val isEvent: Boolean,
    val synced: Boolean,
)

@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: String,
    val operator: String,
    val threshold: Float,
    val action: String,
    val enabled: Boolean,
)
