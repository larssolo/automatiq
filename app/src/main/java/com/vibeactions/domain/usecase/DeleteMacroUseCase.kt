package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.scheduler.AlarmScheduler
import com.vibeactions.scheduler.GeofenceManager
import javax.inject.Inject

class DeleteMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager
) {
    suspend operator fun invoke(macro: Macro) {
        alarmScheduler.cancel(macro)
        geofenceManager.remove(macro)
        repo.delete(macro)
    }
}
