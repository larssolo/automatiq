package com.vibeactions.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vibeactions.domain.model.Macro
import com.vibeactions.util.calculateNextFireTime
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    fun schedule(macro: Macro) {
        val time = macro.scheduledTime ?: return
        val triggerAt = calculateNextFireTime(time)
        val pi = pendingIntent(macro)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (_: SecurityException) {
            // Exact-alarm permission missing; UI banner prompts the user. WorkManager catch-up still covers it.
        }
    }

    fun cancel(macro: Macro) {
        alarmManager.cancel(pendingIntent(macro))
    }

    private fun pendingIntent(macro: Macro): PendingIntent {
        val intent = Intent(context, MacroAlarmReceiver::class.java).apply {
            putExtra(MacroAlarmReceiver.EXTRA_MACRO_ID, macro.id)
        }
        return PendingIntent.getBroadcast(
            context, macro.alarmRequestCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
