package com.vibeactions.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "trigger_type") val triggerType: String,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: String?,
    @ColumnInfo(name = "repeat_daily") val repeatDaily: Boolean,
    @ColumnInfo(name = "recipient_number") val recipientNumber: String,
    @ColumnInfo(name = "message_body") val messageBody: String,
    val enabled: Boolean,
    @ColumnInfo(name = "last_triggered_at") val lastTriggeredAt: Long?,
    @ColumnInfo(name = "last_status") val lastStatus: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_scheduled_fire_at") val lastScheduledFireAt: Long? = null,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    /** Weekday bitmask: bit (day-1) set for ISO day 1=Mon..7=Sun. 127 = all days. */
    @ColumnInfo(name = "days_of_week", defaultValue = "127") val daysOfWeek: Int = 127
)
