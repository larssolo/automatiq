package com.vibeactions.scheduler

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.startOfDayMillis
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.notifications.MacroNotificationManager
import com.vibeactions.sms.SmsDispatcher
import com.vibeactions.util.aiVariationApplies
import com.vibeactions.util.expandTemplate
import com.vibeactions.widget.WidgetRefresher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroFirer @Inject constructor(
    @ApplicationContext private val context: Context,
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
     * Returns the outcome, or null when the fire was skipped (macro missing/disabled, no recipients,
     * or another path already claimed today's scheduled send) or handed off to [AiVaryWorker]
     * (AI-variation macros: the worker calls back with an overrideBody once the text exists).
     */
    suspend fun fire(
        macroId: String,
        enforceOncePerDay: Boolean,
        overrideRecipient: String? = null,
        overrideBody: String? = null,
        suppressResultNotification: Boolean = false
    ): FireResult? {
        val macro = macroRepo.getById(macroId) ?: return null
        if (!macro.enabled) return null
        val now = System.currentTimeMillis()
        // Auto-reply targets the incoming sender; everything else uses the macro's recipient list.
        // Checked before the claim below: an unsendable macro must not consume the day's send.
        val targets = overrideRecipient?.let { listOf(it) } ?: macro.recipients
        if (targets.isEmpty()) return null
        // Scheduled fires (alarm + catch-up worker) dedupe on the scheduled-fire marker only, so a
        // manual/widget tap earlier today does not consume the day's scheduled send. The claim is an
        // atomic check-and-set, so a simultaneous alarm + catch-up can't both pass the guard.
        if (enforceOncePerDay && !macroRepo.tryClaimScheduledFire(macro.id, now, startOfDayMillis(now))) return null

        // AI-varied sends: generating the text takes seconds (Gemini) and may need the user's
        // approval, so it can't happen inline — receivers call fire() on a tight goAsync budget.
        // Hand off to AiVaryWorker, which calls back here with an overrideBody once the text
        // exists (AUTO) or is approved (APPROVE). The claim above stays consumed: the hand-off IS
        // today's scheduled send.
        if (aiVariationApplies(macro.triggerType, macro.aiReplyEnabled,
                hasOverrideBody = overrideBody != null,
                hasOverrideRecipient = overrideRecipient != null)
        ) {
            // Re-arm tomorrow NOW: the normal re-arm below only runs when a send completes, and an
            // APPROVE-mode draft the user ignores would otherwise leave the alarm dead for good.
            if (macro.triggerType == TriggerType.SCHEDULED && macro.repeatDaily) {
                alarmScheduler.schedule(macro)
            }
            enqueueAiVary(macro)
            return null
        }

        // Expand {dato}/{tid}/{ugedag}/{navn} once, then send the same text to every recipient.
        // For reply fires (auto-reply / missed call) the override recipient IS the other party,
        // which is what {afsender} should expand to.
        val body = overrideBody
            ?: expandTemplate(macro.messageBody, LocalDateTime.now(), macro.name, overrideRecipient)
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

        // Finalize the log first, then read it back: a fast radio failure receipt may already have
        // flipped the row to FAILED (terminal in updateResult), and the macro status and
        // notification must mirror that final outcome — not report SUCCESS for a dropped send.
        logRepo.updateResult(logId, status, error)
        val finalLog = logRepo.get(logId)
        val finalStatus = finalLog?.status ?: status
        val finalError = finalLog?.errorMessage ?: error
        macroRepo.updateStatus(macro.id, now, finalStatus)
        // If the receipt won the race, SmsSentReceiver already posted a corrective notification.
        if (!suppressResultNotification && finalStatus == status) {
            notifications.notifyResult(macro, finalStatus, finalError, targets, body)
        }

        if (macro.triggerType == TriggerType.SCHEDULED && macro.repeatDaily) {
            alarmScheduler.schedule(macro.copy(lastTriggeredAt = now, lastStatus = finalStatus))
        }
        // Keep bound home-screen widgets' "Last: …" subtitle in sync for scheduled/auto fires too.
        widgets.refreshFor(macro.id)
        return FireResult(finalStatus, finalError)
    }

    /** Hands an AI-variation fire to [AiVaryWorker]. The fresh event id makes a WorkManager retry
     *  of the same job dedupe (identical inputData) while a later fire mints a new id. Unique work
     *  with REPLACE: a still-pending variation from an earlier fire is superseded, never stacked. */
    private fun enqueueAiVary(macro: Macro) {
        val work = OneTimeWorkRequestBuilder<AiVaryWorker>()
            .setInputData(
                workDataOf(
                    AiVaryWorker.KEY_MACRO_ID to macro.id,
                    AiVaryWorker.KEY_EVENT_ID to java.util.UUID.randomUUID().toString()
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AiVaryWorker.uniqueName(macro.id), ExistingWorkPolicy.REPLACE, work
        )
    }
}

/** Outcome of a completed fire: the final status and, for failures, a short human-readable reason. */
data class FireResult(val status: MacroStatus, val error: String?)
