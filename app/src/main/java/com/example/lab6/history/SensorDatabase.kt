package com.example.lab6.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SensorRecord::class], version = 1)
abstract class SensorDatabase : RoomDatabase() {
    abstract fun sensorDao(): SensorDao

    companion object {
        @Volatile private var instance: SensorDatabase? = null

        fun get(context: Context): SensorDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SensorDatabase::class.java,
                "light_history.db",
            ).build().also { instance = it }
        }
    }
}
