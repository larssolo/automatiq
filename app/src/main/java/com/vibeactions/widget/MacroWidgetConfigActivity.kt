package com.vibeactions.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.Macro
import com.vibeactions.ui.common.StaticBackground
import com.vibeactions.ui.common.compactSummary
import com.vibeactions.ui.common.enableDarkEdgeToEdge
import com.vibeactions.ui.macrolist.MacroListViewModel
import com.vibeactions.ui.theme.DmSans
import com.vibeactions.ui.theme.JetBrainsMono
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Primary
import com.vibeactions.ui.theme.VibeActionsTheme
import com.vibeactions.util.accentColorFor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Opens right after the user adds a macro widget: they pick which macro the new button runs. */
@AndroidEntryPoint
class MacroWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        setContent {
            VibeActionsTheme {
                val vm: MacroListViewModel = hiltViewModel()
                val macros by vm.macros.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    StaticBackground(Modifier.fillMaxSize())
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = WindowInsets.systemBars.asPaddingValues().let {
                            PaddingValues(
                                start = 20.dp, end = 20.dp,
                                top = it.calculateTopPadding() + 32.dp,
                                bottom = it.calculateBottomPadding() + 24.dp
                            )
                        },
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { Header(empty = macros.isEmpty()) }
                        items(macros, key = { it.id }) { macro ->
                            MacroChoice(macro) { bind(widgetId, macro.id) }
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

@Composable
private fun Header(empty: Boolean) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(
            "NEW WIDGET",
            fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, letterSpacing = 1.5.sp, color = Primary
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Pick a macro",
            fontFamily = DmSans, fontWeight = FontWeight.Medium,
            fontSize = 32.sp, lineHeight = 36.sp, color = Color.White, letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (empty) "You have no macros yet. Create one in Automatiq, then add the widget again."
            else "The button on your home screen runs this macro with one tap and shows its name.",
            fontFamily = DmSans, fontSize = 16.sp, lineHeight = 22.sp, color = Color(0xFFD8D8D8)
        )
    }
}

@Composable
private fun MacroChoice(macro: Macro, onClick: () -> Unit) {
    val accent = Color(accentColorFor(macro))
    // Leaf-cut corners echo the macro cards in the main list.
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomEnd = 22.dp, bottomStart = 8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xDE121212))
            .border(1.dp, accent.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center
        ) {
            // Dark accents (indigo, teal) need a white glyph; bright ones read better with black.
            val glyph = if (accent.luminance() > 0.4f) Color.Black else Color.White
            Icon(Icons.AutoMirrored.Filled.Send, null, tint = glyph, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                macro.name, fontFamily = DmSans, fontWeight = FontWeight.Medium,
                fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                compactSummary(macro), fontFamily = JetBrainsMono, fontSize = 12.sp,
                color = OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
