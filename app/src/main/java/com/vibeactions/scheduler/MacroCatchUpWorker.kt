package com.vibeactions.scheduler

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.alreadySentToday
import com.vibeactions.util.isScheduledDay
import com.vibeactions.util.parseHhMmOrNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalTime

@HiltWorker
class MacroCatchUpWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repo: MacroRepository,
    private val logRepo: MacroLogRepository,
    private val firer: MacroFirer
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        // A log row still PENDING this long after its trigger was orphaned by a mid-send process
        // death; finalize it so the log doesn't show an eternal "PENDING".
        logRepo.failStalePending(now - STALE_PENDING_MS)
        val nowTime = LocalTime.now()
        val today = LocalDate.now()
        repo.getEnabledScheduled().forEach { macro ->
            // A one-shot macro (repeatDaily=false, import-only) that already fired is done for good.
            if (!macro.repeatDaily && macro.lastScheduledFireAt != null) return@forEach
            val time = macro.scheduledTime?.let { parseHhMmOrNull(it) } ?: return@forEach
            // Catch up only if today is an active scheduled day (weekday + week-interval/anchor) and
            // the time has passed.
            val passedToday = !nowTime.isBefore(time) &&
                isScheduledDay(today, macro.daysOfWeek, macro.weekInterval, macro.anchorEpochDay,
                    macro.validUntilEpochDay)
            // Dedupe on the scheduled-fire marker (not lastTriggeredAt), so a manual tap today
            // doesn't suppress the catch-up of a missed scheduled fire.
            if (passedToday && !alreadySentToday(macro.lastScheduledFireAt, now)) {
                firer.fire(macro.id, enforceOncePerDay = true)
            }
        }
        return Result.success()
    }

    companion object {
        /** Sends finalize within seconds; 10 minutes is comfortably past any legitimate in-flight send. */
        private const val STALE_PENDING_MS = 10 * 60_000L
    }
}
