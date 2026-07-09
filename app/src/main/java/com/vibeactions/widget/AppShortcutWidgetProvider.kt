package com.vibeactions.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import com.vibeactions.R

/**
 * A home-screen widget that launches another app with one tap — e.g. a bike-controller app you
 * otherwise have to hunt for. Shows the target app's own icon; tapping fires its launch intent
 * directly, so no broadcast round-trip is needed.
 */
class AppShortcutWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderWidget(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { AppShortcutIds.remove(context, it) }
    }

    companion object {
        fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_app_shortcut)
            val pkg = AppShortcutIds.get(context, widgetId)
            val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
            if (pkg != null && launch != null) {
                runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(144, 144) }
                    .onSuccess { views.setImageViewBitmap(R.id.widget_app_icon, it) }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(
                    context, widgetId, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pi)
            }
            manager.updateAppWidget(widgetId, views)
        }
    }
}
