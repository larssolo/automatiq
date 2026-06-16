package com.vibeactions.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.alreadySentToday
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalTime

@HiltWorker
class MacroCatchUpWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: MacroRepository,
    private val firer: MacroFirer
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val nowTime = LocalTime.now()
        repo.getEnabledScheduled().forEach { macro ->
            val time = macro.scheduledTime?.let { LocalTime.parse(it) } ?: return@forEach
            val passedToday = !nowTime.isBefore(time)
            // Dedupe on the scheduled-fire marker (not lastTriggeredAt), so a manual tap today
            // doesn't suppress the catch-up of a missed scheduled fire.
            if (passedToday && !alreadySentToday(macro.lastScheduledFireAt, now)) {
                firer.fire(macro.id, enforceOncePerDay = true)
            }
        }
        return Result.success()
    }
}
