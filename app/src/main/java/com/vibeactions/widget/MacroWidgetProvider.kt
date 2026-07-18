package com.vibeactions.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.vibeactions.R
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.util.accentColorFor
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Renders the macro widgets. Taps are handled by the non-exported [WidgetTapReceiver] — this
 * provider must stay exported for the launcher, so it deliberately holds no tap/fire logic.
 */
class MacroWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun macroRepository(): MacroRepository
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Render off the main thread — the macro name comes from a Room read; runBlocking here
        // would risk an ANR on slow storage.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ids.forEach { renderWidget(context, manager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetIds.remove(context, it) }
    }

    // Fires when the user drags a widget's resize handles — re-render so it can switch between
    // the icon+text layout and the icon-only compact badge for the new size.
    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, widgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, widgetId, newOptions)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                renderWidget(context, manager, widgetId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Below this width there's no room for readable text — fall back to an icon-only badge. */
        private const val COMPACT_WIDTH_THRESHOLD_DP = 90

        suspend fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            val macroId = WidgetIds.get(context, widgetId)
            val width = manager.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, Int.MAX_VALUE)
            val compact = width < COMPACT_WIDTH_THRESHOLD_DP
            val views = RemoteViews(
                context.packageName,
                if (compact) R.layout.widget_macro_compact else R.layout.widget_macro
            )
            if (macroId != null) {
                val macro = ep.macroRepository().getById(macroId)
                if (compact) {
                    macro?.let { views.setInt(R.id.widget_compact_bg, "setColorFilter", accentColorFor(it).toInt()) }
                } else {
                    macro?.let { views.setInt(R.id.widget_icon, "setColorFilter", accentColorFor(it).toInt()) }
                    views.setTextViewText(R.id.widget_name, macro?.name ?: "Macro")
                    views.setTextViewText(R.id.widget_subtitle, "Tap to send")
                }

                val tapIntent = Intent(context, WidgetTapReceiver::class.java).apply {
                    action = WidgetTapReceiver.ACTION_TAP
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
