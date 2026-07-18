package com.vibeactions.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.*
import com.vibeactions.scheduler.AiReplyActionReceiver
import com.vibeactions.ui.common.PermissionBanner
import com.vibeactions.ui.common.StaticBackground
import com.vibeactions.ui.log.LogScreen
import com.vibeactions.ui.macrolist.MacroListScreen
import com.vibeactions.ui.settings.SettingsScreen
import com.vibeactions.ui.theme.VibeActionsTheme
import com.vibeactions.util.maskPhone
import dagger.hilt.android.AndroidEntryPoint

/** A pending AI auto-reply the user opened from a notification, awaiting approve/discard in-app. */
data class AiApproval(val macroId: String, val recipient: String, val body: String, val notifId: Int)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Compose-observable so a notification tap while the app is already open updates the UI too.
    private val pendingApproval = mutableStateOf<AiApproval?>(null)
    // Route a notification (e.g. a result's "View Log"/tap) asks us to open, consumed once by AppRoot.
    private val pendingNav = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingApproval.value = readApproval(intent)
        pendingNav.value = intent.getStringExtra(EXTRA_NAV)
        setContent {
            VibeActionsTheme {
                AppRoot(pendingNav.value, onNavConsumed = { pendingNav.value = null })
                pendingApproval.value?.let { approval ->
                    AiApprovalDialog(approval, onDone = { pendingApproval.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readApproval(intent)?.let { pendingApproval.value = it }
        intent.getStringExtra(EXTRA_NAV)?.let { pendingNav.value = it }
    }

    private fun readApproval(intent: Intent?): AiApproval? {
        if (intent?.getStringExtra(EXTRA_AI_ACTION) != "approve") return null
        val macroId = intent.getStringExtra(EXTRA_MACRO_ID) ?: return null
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT) ?: return null
        val body = intent.getStringExtra(EXTRA_BODY) ?: return null
        return AiApproval(macroId, recipient, body, intent.getIntExtra(EXTRA_NOTIF_ID, -1))
    }

    companion object {
        const val EXTRA_AI_ACTION = "ai_action"
        const val EXTRA_MACRO_ID = "macro_id"
        const val EXTRA_RECIPIENT = "recipient"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_NAV = "nav"
    }
}

/** In-app approval surface for AI auto-replies — reliable on OEM skins (MIUI) where notification
 *  action buttons don't render. Lets the user edit the reply before sending. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiApprovalDialog(approval: AiApproval, onDone: () -> Unit) {
    val context = LocalContext.current
    var text by remember(approval) { mutableStateOf(approval.body) }

    fun cancelNotification() {
        if (approval.notifId != -1) {
            context.getSystemService(NotificationManager::class.java)?.cancel(approval.notifId)
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Send AI reply?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "To ${maskPhone(approval.recipient)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Reply (editable)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    context.sendBroadcast(
                        Intent(context, AiReplyActionReceiver::class.java).apply {
                            action = AiReplyActionReceiver.ACTION_AI_SEND
                            putExtra(AiReplyActionReceiver.EXTRA_MACRO_ID, approval.macroId)
                            putExtra(AiReplyActionReceiver.EXTRA_RECIPIENT, approval.recipient)
                            putExtra(AiReplyActionReceiver.EXTRA_BODY, text)
                            putExtra(AiReplyActionReceiver.EXTRA_NOTIF_ID, approval.notifId)
                        }
                    )
                    onDone()
                }
            ) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = { cancelNotification(); onDone() }) { Text("Discard") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(navTarget: String? = null, onNavConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val context = LocalContext.current

    // A notification asked us to open a specific tab (e.g. the Log). Navigate once, then clear it so
    // it doesn't re-fire on recomposition or config change.
    LaunchedEffect(navTarget) {
        if (navTarget != null) {
            nav.navigate(navTarget) { launchSingleTop = true }
            onNavConsumed()
        }
    }
    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> smsGranted = result[Manifest.permission.SEND_SMS] == true }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) smsPermLauncher.launch(missing.toTypedArray())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.SEND_SMS
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // Tab-style navigation: don't stack a new copy of the destination on every tap — pop back to
    // the start destination and keep at most one instance of each tab on the stack.
    fun navigateTab(target: String) {
        nav.navigate(target) {
            popUpTo(nav.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        StaticBackground(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color(0xDE121212)) {
                    NavigationBarItem(
                        selected = route == "list", onClick = { navigateTab("list") },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, "Macros") }, label = { Text("Macros") })
                    NavigationBarItem(
                        selected = route == "log", onClick = { navigateTab("log") },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, "Log") }, label = { Text("Log") })
                    NavigationBarItem(
                        selected = route == "settings", onClick = { navigateTab("settings") },
                        icon = { Icon(Icons.Default.Settings, "Settings") }, label = { Text("Settings") })
                }
            }
        ) { p ->
            NavHost(nav, startDestination = "list", modifier = Modifier.padding(p)) {
                composable("list") {
                    MacroListScreen(
                        banner = {
                            if (!smsGranted) {
                                PermissionBanner(
                                    "SMS permission is required to send messages.",
                                    "Grant"
                                ) { smsPermLauncher.launch(arrayOf(Manifest.permission.SEND_SMS)) }
                            }
                        }
                    )
                }
                composable("log") { LogScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}
