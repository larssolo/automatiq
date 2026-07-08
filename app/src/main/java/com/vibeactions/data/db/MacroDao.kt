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

    @Query("SELECT * FROM macros WHERE enabled = 1 AND trigger_type = :type")
    suspend fun getEnabledByTrigger(type: String): List<MacroEntity>

    @Upsert
    suspend fun upsert(macro: MacroEntity)

    @Delete
    suspend fun delete(macro: MacroEntity)

    @Query("UPDATE macros SET last_triggered_at = :at, last_status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, at: Long, status: String)

    /** Status-only update for late radio-level failure reports (keeps last_triggered_at intact). */
    @Query("UPDATE macros SET last_status = :status WHERE id = :id")
    suspend fun updateLastStatus(id: String, status: String)

    /**
     * Atomically claim today's scheduled fire: stamps [at] only if no fire has been recorded since
     * local midnight ([startOfDay]). Returns rows updated (1 = claimed by this caller, 0 = another
     * path — alarm vs. catch-up — already claimed today). The atomicity prevents a double-send race.
     */
    @Query(
        "UPDATE macros SET last_scheduled_fire_at = :at " +
            "WHERE id = :id AND (last_scheduled_fire_at IS NULL OR last_scheduled_fire_at < :startOfDay)"
    )
    suspend fun claimScheduledFire(id: String, at: Long, startOfDay: Long): Int

    @Query("UPDATE macros SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)
}
