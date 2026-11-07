package com.vibeactions.ui

import android.Manifest
import com.vibeactions.ui.common.SentenceKeyboard
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.vibeactions.ui.common.enableDarkEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Primary
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.vibeactions.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.vibeactions.data.AppSettings
import com.vibeactions.scheduler.AiReplyActionReceiver
import com.vibeactions.ui.common.AiConsentDialog
import com.vibeactions.ui.common.BrandIntro
import com.vibeactions.ui.common.ReportAiButton
import com.vibeactions.ui.onboarding.OnboardingScreen
import com.vibeactions.util.AiConsent
import com.vibeactions.util.AiReportSource
import com.vibeactions.ui.setup.SetupScreen
import com.vibeactions.ui.setup.missingSetupSteps
import com.vibeactions.ui.common.PermissionBanner
import com.vibeactions.ui.common.ProvideAmbientPulse
import com.vibeactions.ui.common.StaticBackground
import com.vibeactions.ui.health.HealthScreen
import com.vibeactions.ui.log.LogScreen
import com.vibeactions.ui.macrolist.MacroListScreen
import com.vibeactions.ui.settings.SettingsScreen
import com.vibeactions.ui.theme.VibeActionsTheme
import com.vibeactions.util.maskPhone
import dagger.hilt.android.AndroidEntryPoint

/** A pending AI text the user opened from a notification, awaiting approve/discard in-app.
 *  [recipient] is the counterparty for auto-replies; null for an AI-variation (scheduled) draft,
 *  which sends to the macro's own recipient list. [toLabel] is the masked "To …" display text. */
data class AiApproval(
    val macroId: String,
    val recipient: String?,
    val toLabel: String,
    val body: String,
    val notifId: Int,
    val aiNote: String? = null,
    /** The incoming SMS an auto-reply answers; lets the dialog ask for a new suggestion. */
    val incoming: String? = null
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Compose-observable so a notification tap while the app is already open updates the UI too.
    private val pendingApproval = mutableStateOf<AiApproval?>(null)
    // Route a notification (e.g. a result's "View Log"/tap) asks us to open, consumed once by AppRoot.
    private val pendingNav = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableDarkEdgeToEdge()
        // BrandIntro's first frame repeats the system splash, so drop the splash at once instead of
        // letting Android fade it out over the intro (which reads as a flicker).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        publishShortcuts()
        // On recreation (process death, config change) Android hands back the original launch
        // intent; replaying it would yank the user back to the Log / approval dialog every time.
        if (savedInstanceState == null) {
            pendingApproval.value = readApproval(intent)
            pendingNav.value = intent.getStringExtra(EXTRA_NAV)
        }
        // The brand intro plays on a plain cold start only: not on first run (onboarding has its own
        // intro), not after recreation, and not when a notification opened the app for a task.
        val playIntro = savedInstanceState == null && AppSettings.onboardingDone(this) &&
            pendingApproval.value == null && pendingNav.value == null
        setContent {
            VibeActionsTheme {
                var showIntro by rememberSaveable { mutableStateOf(playIntro) }
                var showGuide by rememberSaveable { mutableStateOf(!AppSettings.onboardingDone(this)) }
                if (showGuide) {
                    OnboardingScreen(onFinish = { showGuide = false })
                } else {
                    AppRoot(
                        pendingNav.value,
                        onNavConsumed = { pendingNav.value = null },
                        onShowGuide = { showGuide = true }
                    )
                }
                if (showIntro) BrandIntro(onFinished = { showIntro = false })
                pendingApproval.value?.let { approval ->
                    AiApprovalDialog(approval, onDone = { pendingApproval.value = null })
                }
                // Users who added a key before consent existed: AI is paused until they agree.
                var askConsent by rememberSaveable {
                    mutableStateOf(hasGeminiKey(this) && !AiConsent.given(this))
                }
                if (askConsent && !showGuide && !showIntro && pendingApproval.value == null) {
                    AiConsentDialog(
                        onAccept = { AiConsent.give(this); askConsent = false },
                        onDecline = { askConsent = false }
                    )
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
        // Absent recipient = AI-variation draft (sends to the macro's own recipients).
        val recipient = intent.getStringExtra(EXTRA_RECIPIENT)
        val body = intent.getStringExtra(EXTRA_BODY) ?: return null
        // Fallback chain covers notifications posted by an older app version without the label.
        val toLabel = intent.getStringExtra(EXTRA_TO_LABEL)
            ?: recipient?.let { maskPhone(it) } ?: "recipients"
        return AiApproval(
            macroId, recipient, toLabel, body, intent.getIntExtra(EXTRA_NOTIF_ID, -1),
            intent.getStringExtra(EXTRA_AI_NOTE),
            intent.getStringExtra(EXTRA_INCOMING)
        )
    }

    /** Long-press shortcuts on the launcher icon. Dynamic rather than res/xml so the intents
     *  name this build's own package (the side-by-side ".test" build included). */
    private fun publishShortcuts() {
        fun shortcut(id: String, label: String, icon: Int, nav: String) =
            ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(label)
                .setIcon(IconCompat.createWithResource(this, icon))
                .setIntent(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_NAV, nav)
                )
                .build()
        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(
                this,
                listOf(
                    shortcut("new_macro", "New macro", R.drawable.shortcut_new, NAV_NEW_MACRO),
                    shortcut("log", "Log", R.drawable.shortcut_log, "log")
                )
            )
        }
    }

    private fun hasGeminiKey(context: Context) =
        !context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
            .getString("gemini_api_key", null).isNullOrBlank()

    companion object {
        const val EXTRA_AI_ACTION = "ai_action"
        const val EXTRA_MACRO_ID = "macro_id"
        const val EXTRA_RECIPIENT = "recipient"
        const val EXTRA_TO_LABEL = "to_label"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_NAV = "nav"
        const val EXTRA_AI_NOTE = "ai_note"
        const val EXTRA_INCOMING = "incoming"
        /** EXTRA_NAV value that opens the list with an empty editor. */
        const val NAV_NEW_MACRO = "new_macro"
    }
}

/** In-app approval surface for AI texts (auto-replies and scheduled variations) — reliable on OEM
 *  skins (MIUI) where notification action buttons don't render. Lets the user edit before sending. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        title = { Text(if (approval.recipient != null) "Send AI reply?" else "Send AI message?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "To ${approval.toLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Message (editable)") },
                    keyboardOptions = SentenceKeyboard,
                    modifier = Modifier.fillMaxWidth()
                )
                // Wraps instead of squeezing: three labelled buttons don't fit one line on most phones.
                FlowRow(verticalArrangement = Arrangement.Center) {
                    // Copies the text as it stands now, edits included.
                    TextButton(
                        enabled = text.isNotBlank(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        onClick = { copyToClipboard(context, text) }
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    // A reply can only be rewritten when we still know the message it answers.
                    if (approval.recipient == null || approval.incoming != null) {
                        TextButton(contentPadding = PaddingValues(horizontal = 8.dp), onClick = {
                            context.sendBroadcast(
                                AiReplyActionReceiver.regenerateIntent(
                                    context, approval.macroId, approval.recipient, approval.incoming,
                                    approval.body, approval.notifId
                                )
                            )
                            onDone()
                        }) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New suggestion")
                        }
                    }
                    // Reports what the AI wrote, not the user's edit of it.
                    ReportAiButton(
                        approval.body,
                        if (approval.recipient != null) AiReportSource.AUTO_REPLY else AiReportSource.VARIATION
                    )
                }
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
                            // Absent for a variation draft: the receiver then sends to the
                            // macro's own recipient list.
                            approval.recipient?.let {
                                putExtra(AiReplyActionReceiver.EXTRA_RECIPIENT, it)
                            }
                            putExtra(AiReplyActionReceiver.EXTRA_BODY, text)
                            putExtra(AiReplyActionReceiver.EXTRA_NOTIF_ID, approval.notifId)
                            approval.aiNote?.let { putExtra(AiReplyActionReceiver.EXTRA_AI_NOTE, it) }
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TextFox message", text))
    // Android 13+ shows its own "Copied" confirmation; older versions get a toast.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    navTarget: String? = null,
    onNavConsumed: () -> Unit = {},
    onShowGuide: () -> Unit = {}
) {
    val nav = rememberNavController()
    val context = LocalContext.current

    // Tab-style navigation: at most one instance of each tab, always on top of "list".
    // "list" itself is reached by popping, not by navigate+restoreState: popUpTo("list") with
    // saveState files the popped tabs under "list", so restoring "list" can re-push them and
    // leave the user stuck on the Log.
    fun navigateTab(target: String) {
        if (target == "list") {
            if (!nav.popBackStack("list", inclusive = false)) nav.navigate("list")
            return
        }
        nav.navigate(target) {
            popUpTo("list") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // A notification asked us to open a specific tab (e.g. the Log). Navigate once, then clear it so
    // it doesn't re-fire on recomposition.
    var openNewMacro by remember { mutableStateOf(false) }
    LaunchedEffect(navTarget) {
        if (navTarget != null) {
            if (navTarget == MainActivity.NAV_NEW_MACRO) {
                navigateTab("list")
                openNewMacro = true
            } else navigateTab(navTarget)
            onNavConsumed()
        }
    }
    var missingSetup by remember { mutableIntStateOf(missingSetupSteps(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) missingSetup = missingSetupSteps(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    // Also recount on every screen change: some steps are confirmed inside the app (e.g. "Did you
    // switch on Autostart?"), so no ON_RESUME follows and the banner would linger until a restart.
    LaunchedEffect(route) { missingSetup = missingSetupSteps(context) }

    Box(Modifier.fillMaxSize()) {
      ProvideAmbientPulse {
        StaticBackground(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                // Leaf-cut top corners echo the cards; each tab gets its own icon.
                NavigationBar(
                    containerColor = Color(0xDE121212),
                    modifier = Modifier.clip(
                        RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp)
                    )
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        indicatorColor = Primary.copy(alpha = 0.16f),
                        unselectedIconColor = OnSurfaceVariant,
                        unselectedTextColor = OnSurfaceVariant
                    )
                    NavigationBarItem(
                        selected = route == "list", onClick = { navigateTab("list") },
                        icon = { Icon(Icons.Default.Bolt, "Macros") }, label = { Text("Macros") },
                        colors = navColors)
                    NavigationBarItem(
                        selected = route == "log", onClick = { navigateTab("log") },
                        icon = { Icon(Icons.Default.History, "Log") }, label = { Text("Log") },
                        colors = navColors)
                    NavigationBarItem(
                        selected = route == "settings", onClick = { navigateTab("settings") },
                        icon = { Icon(Icons.Default.Tune, "Settings") }, label = { Text("Settings") },
                        colors = navColors)
                }
            }
        ) { p ->
            // consumeWindowInsets: the screens' own Scaffolds/TopAppBars must not pad for the status
            // and navigation bars a second time — this outer Scaffold already did.
            NavHost(nav, startDestination = "list", modifier = Modifier.padding(p).consumeWindowInsets(p)) {
                composable("list") {
                    MacroListScreen(
                        openNewMacro = openNewMacro,
                        onNewMacroOpened = { openNewMacro = false },
                        banner = {
                            if (missingSetup > 0) {
                                PermissionBanner(
                                    if (missingSetup == 1) "1 setup step left before your macros run reliably."
                                    else "$missingSetup setup steps left before your macros run reliably.",
                                    "Fix"
                                ) { nav.navigate("setup") { launchSingleTop = true } }
                            }
                        }
                    )
                }
                composable("log") { LogScreen() }
                composable("settings") {
                    SettingsScreen(
                        onOpenHealth = { nav.navigate("health") },
                        onOpenSetup = { nav.navigate("setup") },
                        onShowGuide = onShowGuide
                    )
                }
                composable("health") { HealthScreen() }
                composable("setup") { SetupScreen() }
            }
        }
      }
    }
}
