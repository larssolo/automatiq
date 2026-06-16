package com.vibeactions.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.vibeactions.data.db.entities.MacroEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY sort_order ASC, created_at DESC")
    fun observeAll(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getById(id: String): MacroEntity?

    @Query("SELECT * FROM macros WHERE enabled = 1 AND trigger_type = 'SCHEDULED'")
    suspend fun getEnabledScheduled(): List<MacroEntity>

    @Upsert
    suspend fun upsert(macro: MacroEntity)

    @Delete
    suspend fun delete(macro: MacroEntity)

    @Query("UPDATE macros SET last_triggered_at = :at, last_status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, at: Long, status: String)

    @Query("UPDATE macros SET last_scheduled_fire_at = :at WHERE id = :id")
    suspend fun updateScheduledFireAt(id: String, at: Long)

    @Query("UPDATE macros SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)
}
