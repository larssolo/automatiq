package com.vibeactions.domain.model

enum class TriggerType { SCHEDULED, MANUAL, INCOMING, LOCATION, CHARGING, BLUETOOTH, WIFI, MISSED_CALL }
enum class MacroStatus { SUCCESS, FAILED, PENDING }
enum class AiSendMode { APPROVE, AUTO }

/** Radio delivery-report outcome for a sent SMS; null = the carrier reported nothing. */
enum class DeliveryStatus { DELIVERED, FAILED }

/** Trigger types whose firing depends on a device state change (power/Bluetooth/Wi-Fi) monitored by
 *  the foreground [com.vibeactions.scheduler.TriggerMonitorService] rather than an alarm or geofence. */
val STATE_TRIGGERS = setOf(TriggerType.CHARGING, TriggerType.BLUETOOTH, TriggerType.WIFI)

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
    /** INCOMING/MISSED_CALL: reply only when the sender/caller matches (digits-normalised); null/blank = any. */
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
    val geofenceTransition: Int? = null,
    /** INCOMING only: when true, replies via Gemini AI instead of the fixed messageBody. */
    val aiReplyEnabled: Boolean = false,
    /** INCOMING only: APPROVE = notify user to confirm before send; AUTO = send immediately and inform. */
    val aiSendMode: AiSendMode = AiSendMode.APPROVE,
    /** INCOMING + AI only: per-macro instruction steering tone/length of the reply; null/blank = none. */
    val aiReplyInstruction: String? = null,
    /** State triggers (CHARGING/BLUETOOTH/WIFI): fire on connect/arrive (true) or disconnect/leave (false). */
    val triggerOnConnect: Boolean = true,
    /** BLUETOOTH: device MAC address (or WIFI: SSID) to match; null/blank = any device/network. */
    val triggerTarget: String? = null,
    /** BLUETOOTH/WIFI: human-readable label for [triggerTarget] shown in the UI (e.g. the device name). */
    val triggerTargetLabel: String? = null
) {
    /** Stable positive Int request code for PendingIntent, derived from the UUID. */
    fun alarmRequestCode(): Int = (id.hashCode() and 0x7FFFFFFF)
}
