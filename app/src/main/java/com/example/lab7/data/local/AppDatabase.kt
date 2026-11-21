package com.example.lab7.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ReadingEntity::class, RuleEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingDao(): ReadingDao
    abstract fun ruleDao(): RuleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "light_tracker.db")
                .addCallback(object : Callback() {
                    // Приклад сценарію з методички: «якщо X > 10, ввімкнути вібрацію».
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO rules (metric, operator, threshold, action, enabled) " +
                                "VALUES ('ACCEL_X', 'GREATER', 10.0, 'VIBRATE', 1)",
                        )
                    }
                })
                .build()
    }
}
