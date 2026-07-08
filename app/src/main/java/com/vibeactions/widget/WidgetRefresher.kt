package com.vibeactions.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-renders every home-screen widget bound to a macro. Called after each fire so the widget's
 * "Last: …" subtitle reflects scheduled/auto sends too, not just taps on the widget itself.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun refreshFor(macroId: String) {
        val manager = AppWidgetManager.getInstance(context)
        WidgetIds.widgetsFor(context, macroId).forEach { widgetId ->
            MacroWidgetProvider.renderWidget(context, manager, widgetId)
        }
    }
}
