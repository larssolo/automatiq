package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.scheduler.AlarmScheduler
import javax.inject.Inject

class ToggleMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(macro: Macro, enabled: Boolean) {
        val updated = macro.copy(enabled = enabled)
        repo.upsert(updated)
        if (enabled && updated.triggerType == TriggerType.SCHEDULED) {
            alarmScheduler.schedule(updated)
        } else {
            alarmScheduler.cancel(updated)
        }
    }
}
