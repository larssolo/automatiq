package com.vibeactions.domain.model

enum class TriggerType { SCHEDULED, MANUAL, INCOMING, LOCATION }
enum class MacroStatus { SUCCESS, FAILED, PENDING }

/** Geofence transition for a LOCATION macro; values mirror Geofence.GEOFENCE_TRANSITION_*. */
object GeofenceTransition { const val ENTER = 1; const val EXIT = 2 }

data class Macro(
    val id: String,
    val name: String,
    val triggerType: TriggerType,
    val scheduledTime: String?,   // "HH:mm" when SCHEDULED
    val repeatDaily: Boolean = true,
    /** One or more recipient numbers; the macro sends its message to each. Never empty in practice. */
    val recipients: List<String>,
    val messageBody: String,
    val enabled: Boolean = true,
    val lastTriggeredAt: Long? = null,
    val lastStatus: MacroStatus? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** When this macro last fired as a SCHEDULED occurrence. Drives the once-per-day guard so a
     *  manual/widget tap (which updates [lastTriggeredAt]) never blocks the day's scheduled send. */
    val lastScheduledFireAt: Long? = null,
    /** Manual list ordering; lower sorts first. Ties fall back to newest-created-first. */
    val sortOrder: Int = 0,
    /** Allowed weekdays for a SCHEDULED macro (ISO 1=Mon..7=Sun). All seven = every day. */
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    /** Recurrence in weeks: 1 = every week, 2 = every other week, … */
    val weekInterval: Int = 1,
    /** First fire date (epoch day) anchoring the multi-week rhythm; null when [weekInterval] == 1. */
    val anchorEpochDay: Long? = null,
    /** ARGB card accent color (from CardColorPalette). 0 = not yet assigned → UI falls back to primary. */
    val cardColor: Long = 0L,
    /** Last day (epoch day) the macro may fire on, inclusive; null = no expiry. */
    val validUntilEpochDay: Long? = null,
    /** INCOMING only: reply only when the sender matches (digits-normalised); null/blank = any sender. */
    val matchSender: String? = null,
    /** INCOMING only: reply only when the message contains this text (case-insensitive); null/blank = any. */
    val matchKeyword: String? = null,
    /** LOCATION only: geofence centre latitude; null when not a location macro. */
    val latitude: Double? = null,
    /** LOCATION only: geofence centre longitude; null when not a location macro. */
    val longitude: Double? = null,
    /** LOCATION only: geofence radius in metres. */
    val radiusMeters: Float? = null,
    /** LOCATION only: [GeofenceTransition.ENTER] or [GeofenceTransition.EXIT]. */
    val geofenceTransition: Int? = null
) {
    /** Stable positive Int request code for PendingIntent, derived from the UUID. */
    fun alarmRequestCode(): Int = (id.hashCode() and 0x7FFFFFFF)
}
