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

    @Query("SELECT * FROM macro_logs WHERE id = :id")
    suspend fun getById(id: Long): MacroLogEntity?

    @Insert
    suspend fun insert(log: MacroLogEntity): Long

    /**
     * Finalizes a log row's outcome. FAILED is terminal: a radio-level failure report (which can
     * arrive at any time) must not be overwritten by the dispatch-level SUCCESS finalizer.
     */
    @Query("UPDATE macro_logs SET status = :status, error_message = :error WHERE id = :id AND status != 'FAILED'")
    suspend fun updateResult(id: Long, status: String, error: String?)

    /**
     * Records the carrier's delivery report. A FAILED report is terminal (a later part's DELIVERED
     * must not mask it), so this updates unless the row is already FAILED — for a multipart message,
     * any failed part leaves the whole entry FAILED.
     */
    @Query("UPDATE macro_logs SET delivery_status = :status WHERE id = :id AND delivery_status IS NOT 'FAILED'")
    suspend fun updateDelivery(id: Long, status: String)

    /**
     * Fails PENDING rows older than [cutoff]. A row is inserted as PENDING before dispatch and
     * finalized right after — one still PENDING long after its trigger time was orphaned by the
     * process dying mid-send and would otherwise sit as "PENDING" in the log forever.
     */
    @Query("UPDATE macro_logs SET status = 'FAILED', error_message = :error WHERE status = 'PENDING' AND triggered_at < :cutoff")
    suspend fun failStalePending(cutoff: Long, error: String)

    @Query("DELETE FROM macro_logs WHERE id NOT IN (SELECT id FROM macro_logs ORDER BY triggered_at DESC LIMIT :keep)")
    suspend fun prune(keep: Int = 500)

    @Query("DELETE FROM macro_logs")
    suspend fun clear()
}
