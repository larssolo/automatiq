package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.scheduler.AlarmScheduler
import com.vibeactions.scheduler.GeofenceManager
import javax.inject.Inject

class SaveMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager
) {
    suspend operator fun invoke(macro: Macro) {
        repo.upsert(macro)
        alarmScheduler.cancel(macro)   // clear any prior alarm for this id
        geofenceManager.remove(macro)  // clear any prior geofence for this id
        if (macro.enabled && macro.triggerType == TriggerType.SCHEDULED) {
            alarmScheduler.schedule(macro)
        }
        if (macro.enabled && macro.triggerType == TriggerType.LOCATION) {
            geofenceManager.register(macro)
        }
    }
}
