package com.vibeactions.widget

import android.content.Context

object WidgetIds {
    private const val PREFS = "widget_macro_map"
    fun put(context: Context, widgetId: Int, macroId: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(widgetId), macroId).apply()
    fun get(context: Context, widgetId: Int): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(widgetId), null)
    fun remove(context: Context, widgetId: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(widgetId)).apply()
    private fun key(id: Int) = "widget_$id"
}
