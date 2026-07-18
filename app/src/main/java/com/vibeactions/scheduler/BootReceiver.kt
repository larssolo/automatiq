package com.vibeactions.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.vibeactions.domain.usecase.RescheduleAllUseCase
import javax.inject.Inject

/** Re-arms alarms and geofences after the events that silently clear or skew them: device reboot,
 *  app update (every `adb install -r` / sideload cancels AlarmManager alarms and geofences), and
 *  clock/timezone changes (alarms are armed at an absolute epoch-ms computed from local wall-clock
 *  time — after a timezone switch that instant no longer means e.g. "07:00 local"). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var rescheduleAll: RescheduleAllUseCase
    @Inject lateinit var workScheduler: WorkScheduler

    override fun onReceive(context: Context, intent: Intent) {
        // The exact-alarm action (API 31+) fires when the user grants "Alarms & reminders" in
        // system settings — re-arming then upgrades the fallback inexact alarms to setAlarmClock.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != Intent.ACTION_TIMEZONE_CHANGED &&
            intent.action != Intent.ACTION_TIME_CHANGED &&
            intent.action != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleAll()
                workScheduler.ensurePeriodicCatchUp()
            } finally {
                pending.finish()
            }
        }
    }
}
