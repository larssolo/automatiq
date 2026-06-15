package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.scheduler.AlarmScheduler
import javax.inject.Inject

class SaveMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(macro: Macro) {
        repo.upsert(macro)
        alarmScheduler.cancel(macro) // clear any prior alarm for this id
        if (macro.enabled && macro.triggerType == TriggerType.SCHEDULED) {
            alarmScheduler.schedule(macro)
        }
    }
}
