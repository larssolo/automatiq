package com.vibeactions.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.ui.macrolist.MacroListViewModel
import com.vibeactions.ui.theme.VibeActionsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MacroWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            VibeActionsTheme {
                val vm: MacroListViewModel = hiltViewModel()
                val macros by vm.macros.collectAsStateWithLifecycle()
                Scaffold(topBar = { TopAppBar(title = { Text("Pick a macro") }) }) { p ->
                    LazyColumn(Modifier.padding(p).fillMaxSize()) {
                        items(macros, key = { it.id }) { macro ->
                            ListItem(
                                headlineContent = { Text(macro.name) },
                                modifier = Modifier.clickable { bind(widgetId, macro.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun bind(widgetId: Int, macroId: String) {
        WidgetIds.put(this, macroId = macroId, widgetId = widgetId)
        val appContext = applicationContext
        // Render the widget off the main thread (it reads the macro from Room), then close.
        CoroutineScope(Dispatchers.IO).launch {
            MacroWidgetProvider.renderWidget(appContext, AppWidgetManager.getInstance(appContext), widgetId)
        }
        setResult(Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
