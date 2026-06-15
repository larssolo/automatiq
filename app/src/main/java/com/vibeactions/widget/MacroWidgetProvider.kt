package com.vibeactions.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vibeactions.R
import com.vibeactions.data.repository.MacroRepository
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MacroWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun macroRepository(): MacroRepository
        fun firer(): com.vibeactions.scheduler.MacroFirer
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderWidget(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetIds.remove(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TAP) {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val macroId = WidgetIds.get(context, widgetId) ?: return
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            CoroutineScope(Dispatchers.IO).launch {
                ep.firer().fire(macroId, enforceOncePerDay = false)
                renderWidget(context, AppWidgetManager.getInstance(context), widgetId)
            }
        }
    }

    companion object {
        const val ACTION_TAP = "com.vibeactions.widget.ACTION_TAP"

        fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val macroId = WidgetIds.get(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_macro)
            if (macroId != null) {
                val macro = runBlocking { ep.macroRepository().getById(macroId) }
                views.setTextViewText(R.id.widget_name, macro?.name ?: "Macro")
                val subtitle = macro?.lastTriggeredAt?.let {
                    "Last: " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(it))
                } ?: "Tap to send"
                views.setTextViewText(R.id.widget_subtitle, subtitle)

                val tapIntent = Intent(context, MacroWidgetProvider::class.java).apply {
                    action = ACTION_TAP
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                val pi = PendingIntent.getBroadcast(
                    context, widgetId, tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pi)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
