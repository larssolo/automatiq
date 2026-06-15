package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.scheduler.AlarmScheduler
import javax.inject.Inject

class DeleteMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke(macro: Macro) {
        alarmScheduler.cancel(macro)
        repo.delete(macro)
    }
}
