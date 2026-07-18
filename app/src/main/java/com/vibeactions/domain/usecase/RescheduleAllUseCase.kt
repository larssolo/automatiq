package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.scheduler.AlarmScheduler
import com.vibeactions.scheduler.GeofenceManager
import javax.inject.Inject

class RescheduleAllUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler,
    private val geofenceManager: GeofenceManager
) {
    suspend operator fun invoke() {
        // One-shot macros (repeatDaily=false, only creatable via import) must not be re-armed
        // once they have fired — without this filter they would fire again every day.
        repo.getEnabledScheduled()
            .filter { it.repeatDaily || it.lastScheduledFireAt == null }
            .forEach { alarmScheduler.schedule(it) }
        // Geofences don't survive a reboot — re-register every enabled LOCATION macro.
        repo.getEnabledByTrigger(TriggerType.LOCATION).forEach { geofenceManager.register(it) }
    }
}
