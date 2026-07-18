package com.vibeactions.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.scheduler.MacroFirer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles taps on macro widgets. Deliberately separate from the AppWidgetProvider and NOT exported:
 * a tap sends an SMS, and the provider must stay exported for the launcher's APPWIDGET_UPDATE
 * broadcasts — handling the tap there would let any co-installed app fire macros by broadcasting
 * the tap action. Only PendingIntents created by this app can reach this receiver.
 */
@AndroidEntryPoint
class WidgetTapReceiver : BroadcastReceiver() {
    @Inject lateinit var firer: MacroFirer
    @Inject lateinit var repo: MacroRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAP) return
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val macroId = WidgetIds.get(context, widgetId) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                firer.fire(macroId, enforceOncePerDay = false)
                MacroWidgetProvider.renderWidget(context, AppWidgetManager.getInstance(context), widgetId)
                val macro = repo.getById(macroId)
                if (macro != null) {
                    val msg = if (macro.lastStatus == MacroStatus.SUCCESS) {
                        "Sent: ${macro.name}"
                    } else {
                        "Failed: ${macro.name}"
                    }
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context.applicationContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TAP = "com.vibeactions.widget.ACTION_TAP"
    }
}
