package com.vibeactions.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vibeactions.data.db.entities.MacroLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroLogDao {
    @Query("SELECT * FROM macro_logs ORDER BY triggered_at DESC")
    fun observeAll(): Flow<List<MacroLogEntity>>

    @Insert
    suspend fun insert(log: MacroLogEntity): Long

    /**
     * Finalizes a log row's outcome. FAILED is terminal: a radio-level failure report (which can
     * arrive at any time) must not be overwritten by the dispatch-level SUCCESS finalizer.
     */
    @Query("UPDATE macro_logs SET status = :status, error_message = :error WHERE id = :id AND status != 'FAILED'")
    suspend fun updateResult(id: Long, status: String, error: String?)

    @Query("DELETE FROM macro_logs WHERE id NOT IN (SELECT id FROM macro_logs ORDER BY triggered_at DESC LIMIT :keep)")
    suspend fun prune(keep: Int = 500)

    @Query("DELETE FROM macro_logs")
    suspend fun clear()
}
