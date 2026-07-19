package com.vibeactions.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "card_color") val cardColor: Long,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "1") val expanded: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
