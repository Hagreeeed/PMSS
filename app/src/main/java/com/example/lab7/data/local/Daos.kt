package com.example.lab7.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reading: ReadingEntity): Long

    @Query("SELECT * FROM readings ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<ReadingEntity>>

    @Query("SELECT * FROM readings WHERE isEvent = :isEvent ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeByKind(isEvent: Boolean, limit: Int): Flow<List<ReadingEntity>>

    @Query("SELECT COUNT(*) FROM readings WHERE synced = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM readings WHERE synced = 0 ORDER BY id LIMIT :limit")
    suspend fun pending(limit: Int): List<ReadingEntity>

    @Query("UPDATE readings SET synced = 1 WHERE sampleId IN (:sampleIds)")
    suspend fun markSynced(sampleIds: List<String>)

    /** Обмеження розміру БД: видаляє найстаріші вже відправлені записи понад [keep]. */
    @Query("""
        DELETE FROM readings WHERE synced = 1 AND id NOT IN (
            SELECT id FROM readings ORDER BY id DESC LIMIT :keep
        )
    """)
    suspend fun trimSynced(keep: Int): Int
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY id")
    fun observeAll(): Flow<List<RuleEntity>>

    @Upsert
    suspend fun upsert(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: Long)
}
