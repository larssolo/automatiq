package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.scheduler.AlarmScheduler
import com.vibeactions.scheduler.GeofenceManager
import com.vibeactions.scheduler.TriggerMonitor
import com.vibeactions.widget.WidgetRefresher
import javax.inject.Inject

class SaveMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager,
    private val widgets: WidgetRefresher,
    private val triggerMonitor: TriggerMonitor
) {
    suspend operator fun invoke(macro: Macro) {
        repo.upsert(macro)
        alarmScheduler.cancel(macro)   // clear any prior alarm for this id
        geofenceManager.remove(macro)  // clear any prior geofence for this id
        if (macro.enabled && macro.triggerType == TriggerType.SCHEDULED) {
            alarmScheduler.schedule(macro)
        }
        if (macro.enabled && macro.triggerType == TriggerType.LOCATION) {
            geofenceManager.register(macro, notifyOnFailure = true)
        }
        // Start/stop the device-state monitor to match the new set of state-trigger macros.
        triggerMonitor.sync()
        // A rename/recolor must reach any home-screen widget bound to this macro right away —
        // without this the widget shows the old name until the macro next fires.
        widgets.refreshFor(macro.id)
    }
}
