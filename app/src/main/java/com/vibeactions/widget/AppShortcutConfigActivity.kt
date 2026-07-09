package com.vibeactions.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.vibeactions.ui.theme.VibeActionsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(val label: String, val packageName: String, val icon: Bitmap)

/** Widget configuration: pick which installed app the shortcut widget should launch. */
@OptIn(ExperimentalMaterial3Api::class)
class AppShortcutConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            VibeActionsTheme {
                val apps by produceState<List<AppEntry>?>(initialValue = null) {
                    value = withContext(Dispatchers.IO) { loadLaunchableApps() }
                }
                Scaffold(topBar = { TopAppBar(title = { Text("Pick an app") }) }) { p ->
                    val list = apps
                    if (list == null) {
                        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(Modifier.padding(p).fillMaxSize()) {
                            items(list, key = { it.packageName }) { app ->
                                ListItem(
                                    leadingContent = {
                                        Image(
                                            bitmap = app.icon.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    },
                                    headlineContent = { Text(app.label) },
                                    modifier = Modifier.clickable { bind(widgetId, app.packageName) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val pm = packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm).toBitmap(96, 96)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun bind(widgetId: Int, pkg: String) {
        AppShortcutIds.put(this, widgetId = widgetId, packageName = pkg)
        AppShortcutWidgetProvider.renderWidget(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
