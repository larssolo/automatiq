package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.scheduler.AlarmScheduler
import com.vibeactions.scheduler.GeofenceManager
import javax.inject.Inject

class ToggleMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager
) {
    suspend operator fun invoke(macro: Macro, enabled: Boolean) {
        val updated = macro.copy(enabled = enabled)
        repo.upsert(updated)
        // Clear any prior registration for this id, then (re)arm only the trigger this macro uses.
        // Without the geofence branch, re-enabling a LOCATION macro never re-registers its fence
        // (so it silently never fires), and disabling leaves the fence registered.
        alarmScheduler.cancel(updated)
        geofenceManager.remove(updated)
        if (enabled) {
            when (updated.triggerType) {
                TriggerType.SCHEDULED -> alarmScheduler.schedule(updated)
                TriggerType.LOCATION -> geofenceManager.register(updated)
                else -> Unit // INCOMING/MANUAL need no scheduling; the receiver checks enabled at fire time.
            }
        }
    }
}
