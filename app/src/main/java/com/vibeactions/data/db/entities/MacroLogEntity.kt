package com.vibeactions.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macro_logs")
data class MacroLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "macro_id") val macroId: String,
    @ColumnInfo(name = "triggered_at") val triggeredAt: Long,
    val status: String,
    @ColumnInfo(name = "message_preview") val messagePreview: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "delivery_status") val deliveryStatus: String? = null
)
