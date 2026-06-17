package com.vibeactions.scheduler

import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.alreadySentToday
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.sms.SmsDispatcher
import com.vibeactions.util.expandTemplate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroFirer @Inject constructor(
    private val macroRepo: MacroRepository,
    private val logRepo: MacroLogRepository,
    private val sms: SmsDispatcher,
    private val notifications: MacroNotificationManager,
    private val alarmScheduler: AlarmScheduler
) {
    /**
     * Sends the macro's SMS, logs, notifies, updates status, and (for scheduled+repeat) re-arms tomorrow.
     * [enforceOncePerDay] guards scheduled fires against alarm+worker double-send; manual taps pass false.
     * [overrideRecipient] (auto-reply) sends to that number instead of the macro's own recipient list.
     */
    suspend fun fire(macroId: String, enforceOncePerDay: Boolean, overrideRecipient: String? = null) {
        val macro = macroRepo.getById(macroId) ?: return
        if (!macro.enabled) return
        val now = System.currentTimeMillis()
        // Scheduled fires (alarm + catch-up worker) dedupe on the scheduled-fire marker only, so a
        // manual/widget tap earlier today does not consume the day's scheduled send.
        if (enforceOncePerDay && alreadySentToday(macro.lastScheduledFireAt, now)) return

        // Auto-reply targets the incoming sender; everything else uses the macro's recipient list.
        val targets = overrideRecipient?.let { listOf(it) } ?: macro.recipients
        if (targets.isEmpty()) return
        // Expand {dato}/{tid}/{ugedag}/{navn} once, then send the same text to every recipient.
        val body = expandTemplate(macro.messageBody, LocalDateTime.now(), macro.name)
        // Send to every recipient; success only if all succeed, otherwise FAILED with a summary error.
        val failures = targets.mapNotNull { number ->
            sms.send(number, body).exceptionOrNull()?.let { number to it }
        }
        val status = if (failures.isEmpty()) MacroStatus.SUCCESS else MacroStatus.FAILED
        val error = when {
            failures.isEmpty() -> null
            failures.size == targets.size -> failures.first().second.message ?: "send failed"
            else -> "${failures.size}/${targets.size} failed: " + failures.first().second.message
        }

        macroRepo.updateStatus(macro.id, now, status)
        if (enforceOncePerDay) macroRepo.updateScheduledFireAt(macro.id, now)
        logRepo.add(
            MacroLog(
                macroId = macro.id, triggeredAt = now, status = status,
                messagePreview = body.take(40), errorMessage = error
            )
        )
        notifications.notifyResult(macro, status, error)

        if (macro.triggerType == TriggerType.SCHEDULED && macro.repeatDaily) {
            alarmScheduler.schedule(macro.copy(lastTriggeredAt = now, lastStatus = status))
        }
    }
}
