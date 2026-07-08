package com.vibeactions.scheduler

import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.startOfDayMillis
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.sms.SmsDispatcher
import com.vibeactions.util.expandTemplate
import com.vibeactions.widget.WidgetRefresher
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroFirer @Inject constructor(
    private val macroRepo: MacroRepository,
    private val logRepo: MacroLogRepository,
    private val sms: SmsDispatcher,
    private val notifications: MacroNotificationManager,
    private val alarmScheduler: AlarmScheduler,
    private val widgets: WidgetRefresher
) {
    /**
     * Sends the macro's SMS, logs, notifies, updates status, and (for scheduled+repeat) re-arms tomorrow.
     * [enforceOncePerDay] guards scheduled fires against alarm+worker double-send; manual taps pass false.
     * [overrideRecipient] (auto-reply) sends to that number instead of the macro's own recipient list.
     * [overrideBody] when set, uses this text directly instead of expanding the macro's template.
     * [suppressResultNotification] when true, skips posting the result notification.
     */
    suspend fun fire(
        macroId: String,
        enforceOncePerDay: Boolean,
        overrideRecipient: String? = null,
        overrideBody: String? = null,
        suppressResultNotification: Boolean = false
    ) {
        val macro = macroRepo.getById(macroId) ?: return
        if (!macro.enabled) return
        val now = System.currentTimeMillis()
        // Scheduled fires (alarm + catch-up worker) dedupe on the scheduled-fire marker only, so a
        // manual/widget tap earlier today does not consume the day's scheduled send. The claim is an
        // atomic check-and-set, so a simultaneous alarm + catch-up can't both pass the guard.
        if (enforceOncePerDay && !macroRepo.tryClaimScheduledFire(macro.id, now, startOfDayMillis(now))) return

        // Auto-reply targets the incoming sender; everything else uses the macro's recipient list.
        val targets = overrideRecipient?.let { listOf(it) } ?: macro.recipients
        if (targets.isEmpty()) return
        // Expand {dato}/{tid}/{ugedag}/{navn} once, then send the same text to every recipient.
        val body = overrideBody ?: expandTemplate(macro.messageBody, LocalDateTime.now(), macro.name)
        // The log row is created before sending so each SMS can carry a sent receipt addressing it:
        // a radio-level failure later flips this entry (and the macro status) to FAILED.
        val logId = logRepo.add(
            MacroLog(
                macroId = macro.id, triggeredAt = now, status = MacroStatus.PENDING,
                messagePreview = body, errorMessage = null
            )
        )
        // Send to every recipient; success only if all succeed, otherwise FAILED with a summary error.
        val failures = targets.mapNotNull { number ->
            sms.send(number, body, logId, macro.id).exceptionOrNull()?.let { number to it }
        }
        val status = if (failures.isEmpty()) MacroStatus.SUCCESS else MacroStatus.FAILED
        val error = when {
            failures.isEmpty() -> null
            failures.size == targets.size -> failures.first().second.message ?: "send failed"
            else -> "${failures.size}/${targets.size} failed: " + failures.first().second.message
        }

        macroRepo.updateStatus(macro.id, now, status)
        // The scheduled-fire marker was already stamped atomically by tryClaimScheduledFire above.
        logRepo.updateResult(logId, status, error)
        if (!suppressResultNotification) notifications.notifyResult(macro, status, error, targets, body)

        if (macro.triggerType == TriggerType.SCHEDULED && macro.repeatDaily) {
            alarmScheduler.schedule(macro.copy(lastTriggeredAt = now, lastStatus = status))
        }
        // Keep bound home-screen widgets' "Last: …" subtitle in sync for scheduled/auto fires too.
        widgets.refreshFor(macro.id)
    }
}
