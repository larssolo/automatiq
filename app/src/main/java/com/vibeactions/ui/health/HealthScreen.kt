package com.vibeactions.ui.health

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.data.AppSettings
import com.vibeactions.ui.theme.Amber
import com.vibeactions.ui.theme.ErrorRed
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.OutlineVariant
import com.vibeactions.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(vm: HealthViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val macros by vm.macroHealth.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("EEE dd/MM HH:mm", Locale.getDefault()) }

    // Recomputed each time the screen is (re)entered so returning from Settings reflects new grants.
    var refreshKey by remember { mutableStateOf(0) }
    val exactAlarms = remember(refreshKey) { canScheduleExact(context) }
    val batteryUnrestricted = remember(refreshKey) { ignoresBatteryOptimizations(context) }
    val notificationsOn = remember(refreshKey) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val lastCatchUp = remember(refreshKey) { AppSettings.lastCatchUpAt(context) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Health check") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = { TextButton(onClick = { refreshKey++ }) { Text("Refresh") } }
            )
        }
    ) { p ->
        LazyColumn(
            Modifier.padding(p).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("System", style = MaterialTheme.typography.titleSmall, color = OnSurface,
                    modifier = Modifier.padding(top = 8.dp))
            }
            item {
                StatusRow(
                    ok = exactAlarms,
                    title = "Exact alarms",
                    detail = if (exactAlarms) "Allowed — scheduled sends fire on time"
                        else "Blocked — scheduled sends may be delayed",
                    actionLabel = if (exactAlarms) null else "Fix",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                )
            }
            item {
                StatusRow(
                    ok = batteryUnrestricted,
                    title = "Battery optimisation",
                    detail = if (batteryUnrestricted) "Unrestricted — alarms won't be deferred"
                        else "Restricted — alarms may be delayed in deep sleep",
                    actionLabel = if (batteryUnrestricted) null else "Fix",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"))
                        )
                    }
                )
            }
            item {
                StatusRow(
                    ok = notificationsOn,
                    title = "Notifications",
                    detail = if (notificationsOn) "Enabled" else "Disabled — you won't see send results",
                    actionLabel = if (notificationsOn) null else "Fix",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                )
            }
            item {
                StatusRow(
                    ok = null,
                    title = "Last background check",
                    detail = if (lastCatchUp == 0L) "Not run yet (runs about hourly)"
                        else "Ran ${fmt.format(Date(lastCatchUp))}"
                )
            }

            item {
                Text("Active macros", style = MaterialTheme.typography.titleSmall, color = OnSurface,
                    modifier = Modifier.padding(top = 16.dp))
            }
            if (macros.isEmpty()) {
                item { Text("No enabled macros.", color = OnSurfaceVariant, fontSize = 13.sp) }
            } else {
                items(macros) { m ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(m.name, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        val detail = when {
                            m.note != null -> m.note
                            m.nextFireAt != null -> "Next: ${fmt.format(Date(m.nextFireAt))}"
                            else -> m.trigger
                        }
                        Text(
                            detail,
                            color = if (m.note != null) Amber else OnSurfaceVariant,
                            fontSize = 12.sp
                        )
                        HorizontalDivider(color = OutlineVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    ok: Boolean?,
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val dot = when (ok) {
            true -> Primary
            false -> ErrorRed
            null -> OnSurfaceVariant
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = OnSurfaceVariant, fontSize = 12.sp)
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun canScheduleExact(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    else true

private fun ignoresBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
