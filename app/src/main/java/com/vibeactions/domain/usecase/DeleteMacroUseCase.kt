package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.scheduler.AlarmScheduler
import com.vibeactions.scheduler.GeofenceManager
import com.vibeactions.scheduler.TriggerMonitor
import com.vibeactions.widget.WidgetRefresher
import javax.inject.Inject

class DeleteMacroUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager,
    private val widgets: WidgetRefresher,
    private val triggerMonitor: TriggerMonitor
) {
    suspend operator fun invoke(macro: Macro) {
        alarmScheduler.cancel(macro)
        geofenceManager.remove(macro)
        repo.delete(macro)
        // Stop the monitor if this was the last enabled state-trigger macro.
        triggerMonitor.sync()
        // Re-render any widget bound to this macro so it stops showing the deleted macro's name.
        // The widget-id mapping is kept: an Undo (re-save with the same id) restores the binding.
        widgets.refreshFor(macro.id)
    }
}
