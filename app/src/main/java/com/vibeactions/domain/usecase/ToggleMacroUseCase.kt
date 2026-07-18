package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.scheduler.AlarmScheduler
import com.vibeactions.scheduler.GeofenceManager
import com.vibeactions.scheduler.TriggerMonitor
import javax.inject.Inject

class ToggleMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager,
    private val triggerMonitor: TriggerMonitor
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
                TriggerType.LOCATION -> geofenceManager.register(updated, notifyOnFailure = true)
                else -> Unit // INCOMING/MANUAL/state triggers need no per-macro arming here.
            }
        }
        // Enabling/disabling a CHARGING/BLUETOOTH/WIFI macro starts/stops the monitor service.
        triggerMonitor.sync()
    }
}
