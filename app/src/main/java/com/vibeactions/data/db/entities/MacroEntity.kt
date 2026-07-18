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
    @ColumnInfo(name = "days_of_week", defaultValue = "127") val daysOfWeek: Int = 127,
    @ColumnInfo(name = "week_interval", defaultValue = "1") val weekInterval: Int = 1,
    @ColumnInfo(name = "anchor_epoch_day") val anchorEpochDay: Long? = null,
    @ColumnInfo(name = "card_color", defaultValue = "0") val cardColor: Long = 0L,
    @ColumnInfo(name = "valid_until_epoch_day") val validUntilEpochDay: Long? = null,
    @ColumnInfo(name = "match_sender") val matchSender: String? = null,
    @ColumnInfo(name = "match_keyword") val matchKeyword: String? = null,
    @ColumnInfo(name = "latitude") val latitude: Double? = null,
    @ColumnInfo(name = "longitude") val longitude: Double? = null,
    @ColumnInfo(name = "radius_meters") val radiusMeters: Float? = null,
    @ColumnInfo(name = "geofence_transition") val geofenceTransition: Int? = null,
    @ColumnInfo(name = "ai_reply_enabled", defaultValue = "0") val aiReplyEnabled: Boolean = false,
    @ColumnInfo(name = "ai_send_mode", defaultValue = "APPROVE") val aiSendMode: String = "APPROVE",
    @ColumnInfo(name = "ai_reply_instruction") val aiReplyInstruction: String? = null,
    @ColumnInfo(name = "trigger_on_connect", defaultValue = "1") val triggerOnConnect: Boolean = true,
    @ColumnInfo(name = "trigger_target") val triggerTarget: String? = null,
    @ColumnInfo(name = "trigger_target_label") val triggerTargetLabel: String? = null
)
