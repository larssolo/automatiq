package com.vibeactions

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VibeActionsApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifications: com.vibeactions.notifications.MacroNotificationManager
    @Inject lateinit var workScheduler: com.vibeactions.scheduler.WorkScheduler
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannel()
        workScheduler.ensurePeriodicCatchUp()
    }
}
