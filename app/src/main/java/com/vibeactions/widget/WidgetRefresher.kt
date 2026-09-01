package com.vibeactions.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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

    /**
     * Re-renders every widget the app owns. The app-shortcut widget draws the target app's real
     * icon only when its provider runs; a reinstall or reboot leaves it showing the layout's
     * placeholder arrow until the system happens to call onUpdate (which some OEM launchers skip).
     * Calling this on app start rebuilds all widgets from their stored mapping, so their icons
     * self-heal the same way alarms/geofences do.
     */
    suspend fun refreshAll() {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, AppShortcutWidgetProvider::class.java))
            .forEach { AppShortcutWidgetProvider.renderWidget(context, manager, it) }
        manager.getAppWidgetIds(ComponentName(context, MacroWidgetProvider::class.java))
            .forEach { MacroWidgetProvider.renderWidget(context, manager, it) }
    }
}
