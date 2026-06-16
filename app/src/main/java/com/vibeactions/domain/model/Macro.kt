package com.vibeactions.domain.model

enum class TriggerType { SCHEDULED, MANUAL }
enum class MacroStatus { SUCCESS, FAILED, PENDING }

data class Macro(
    val id: String,
    val name: String,
    val triggerType: TriggerType,
    val scheduledTime: String?,   // "HH:mm" when SCHEDULED
    val repeatDaily: Boolean = true,
    val recipientNumber: String,
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
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)
) {
    /** Stable positive Int request code for PendingIntent, derived from the UUID. */
    fun alarmRequestCode(): Int = (id.hashCode() and 0x7FFFFFFF)
}
