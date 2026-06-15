package com.vibeactions.scheduler

import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.alreadySentToday
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.sms.SmsDispatcher
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
     */
    suspend fun fire(macroId: String, enforceOncePerDay: Boolean) {
        val macro = macroRepo.getById(macroId) ?: return
        if (!macro.enabled) return
        val now = System.currentTimeMillis()
        if (enforceOncePerDay && alreadySentToday(macro.lastTriggeredAt, now)) return

        val result = sms.send(macro.recipientNumber, macro.messageBody)
        val status = if (result.isSuccess) MacroStatus.SUCCESS else MacroStatus.FAILED
        val error = result.exceptionOrNull()?.message

        macroRepo.updateStatus(macro.id, now, status)
        logRepo.add(
            MacroLog(
                macroId = macro.id, triggeredAt = now, status = status,
                messagePreview = macro.messageBody.take(40), errorMessage = error
            )
        )
        notifications.notifyResult(macro, status, error)

        if (macro.triggerType == TriggerType.SCHEDULED && macro.repeatDaily) {
            alarmScheduler.schedule(macro.copy(lastTriggeredAt = now, lastStatus = status))
        }
    }
}
