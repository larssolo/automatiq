package com.vibeactions

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VibeActionsApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifications: com.vibeactions.notifications.MacroNotificationManager
    @Inject lateinit var workScheduler: com.vibeactions.scheduler.WorkScheduler
    @Inject lateinit var rescheduleAll: com.vibeactions.domain.usecase.RescheduleAllUseCase
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannel()
        workScheduler.ensurePeriodicCatchUp()
        // Self-heal on every launch: alarms/geofences are silently cleared by force-stop and by
        // events no receiver covers reliably on OEM ROMs. Re-arming is idempotent (same
        // PendingIntent / geofence request id replaces the old registration).
        CoroutineScope(Dispatchers.IO).launch { rescheduleAll() }
        com.vibeactions.ui.common.BackgroundSetting.load(this)
    }
}
