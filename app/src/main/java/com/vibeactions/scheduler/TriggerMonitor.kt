package com.vibeactions.scheduler

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.vibeactions.data.repository.MacroRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts or stops [TriggerMonitorService] to match the current macros: the service (and its
 * foreground notification) exists only while at least one CHARGING/BLUETOOTH/WIFI macro is enabled.
 * Called after every macro change and on app start / boot, so the monitor is self-managing.
 */
@Singleton
class TriggerMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: MacroRepository
) {
    suspend fun sync() {
        val intent = Intent(context, TriggerMonitorService::class.java)
        if (repo.hasEnabledStateTriggers()) {
            // Starting from the background is restricted on API 31+; if it's disallowed right now the
            // monitor simply starts on the next foreground pass rather than crashing.
            runCatching { ContextCompat.startForegroundService(context, intent) }
        } else {
            runCatching { context.stopService(intent) }
        }
    }
}
