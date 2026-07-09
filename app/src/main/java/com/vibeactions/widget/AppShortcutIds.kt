package com.vibeactions.widget

import android.content.Context

/** Maps app-shortcut widget ids to the package name they launch (mirrors [WidgetIds]). */
object AppShortcutIds {
    private const val PREFS = "widget_app_map"
    fun put(context: Context, widgetId: Int, packageName: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(widgetId), packageName).apply()
    fun get(context: Context, widgetId: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(widgetId), null)
    fun remove(context: Context, widgetId: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(widgetId)).apply()
    private fun key(id: Int) = "widget_$id"
}
