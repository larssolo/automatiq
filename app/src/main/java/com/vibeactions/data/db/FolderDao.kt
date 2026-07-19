package com.vibeactions.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.vibeactions.data.db.entities.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sort_order ASC, created_at DESC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): FolderEntity?

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("UPDATE folders SET expanded = :expanded WHERE id = :id")
    suspend fun updateExpanded(id: String, expanded: Boolean)

    @Query("UPDATE folders SET sort_order = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)
}
