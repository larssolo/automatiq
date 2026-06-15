package com.vibeactions.domain.usecase

import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.scheduler.AlarmScheduler
import javax.inject.Inject

class RescheduleAllUseCase @Inject constructor(
    private val repo: MacroRepository,
    private val alarmScheduler: AlarmScheduler
) {
    suspend operator fun invoke() {
        repo.getEnabledScheduled().forEach { alarmScheduler.schedule(it) }
    }
}
