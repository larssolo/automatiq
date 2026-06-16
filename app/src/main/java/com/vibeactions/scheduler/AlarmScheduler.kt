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
        val triggerAt = calculateNextFireTime(time, days = macro.daysOfWeek)
        val pi = pendingIntent(macro)
        // setAlarmClock fires at the exact wall-clock time and is exempt from Doze and battery
        // optimisation — the only reliable way to hit a daily time on stock + OEM Android. It does
        // NOT require SCHEDULE_EXACT_ALARM. We still fall back to an idle-allowed alarm on the rare
        // device where exact alarms are blocked, rather than failing silently.
        if (canScheduleExact()) {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent()), pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(macro: Macro) {
        alarmManager.cancel(pendingIntent(macro))
    }

    /** Shown when the user taps the system alarm-clock icon; just opens the app. */
    private fun showIntent(): PendingIntent {
        val intent = Intent(context, com.vibeactions.ui.MainActivity::class.java)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
