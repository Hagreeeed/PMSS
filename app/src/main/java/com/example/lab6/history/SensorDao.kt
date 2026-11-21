package com.example.lab6.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {
    @Insert
    suspend fun insert(record: SensorRecord)

    @Query("""
        SELECT * FROM sensor_records
        WHERE actionTriggered = :actionTriggered
        ORDER BY timestamp DESC, id DESC LIMIT :limit
    """)
    fun observeLatest(actionTriggered: Boolean, limit: Int): Flow<List<SensorRecord>>
}
