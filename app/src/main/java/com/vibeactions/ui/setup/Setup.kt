package com.vibeactions.ui.setup

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.os.Process
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One thing the phone must allow before macros run reliably. Either a runtime permission
 * ([permissions]) or a system screen ([intent]). [check] reads the state; it may answer null
 * ("can't tell"), and a missing check means it can never be read (Xiaomi's own screens). In both
 * cases the user is asked [confirmQuestion] on return, and only a "yes" marks the step done.
 */
class SetupStep(
    val id: String,
    val icon: ImageVector,
    val title: String,
    val why: String,
    /** What to do on the system screen — also shown as a toast over that screen. */
    val howTo: String,
    val permissions: Array<String> = emptyArray(),
    val intent: ((Context) -> Intent)? = null,
    val check: ((Context) -> Boolean?)? = null,
    val confirmQuestion: String? = null
)

private const val PREFS = "setup_steps"

fun isXiaomi() = Build.MANUFACTURER.equals("xiaomi", true) ||
    Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

private fun pkgUri(context: Context) = Uri.parse("package:${context.packageName}")

/**
 * Xiaomi Autostart for this app: true/false when MIUI/HyperOS will say, null when it can't be read.
 * There's no public API; these are the hooks the system itself uses, so either may be missing.
 */
fun xiaomiAutostartEnabled(context: Context): Boolean? {
    runCatching {
        val m = Class.forName("android.miui.AppOpsUtils")
            .getDeclaredMethod("getApplicationAutoStart", Context::class.java, String::class.java)
        return (m.invoke(null, context, context.packageName) as Int) == 0
    }
    runCatching {
        // MIUI's own app-op for Autostart.
        val ops = context.getSystemService(AppOpsManager::class.java)
        val m = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
        )
        when (m.invoke(ops, 10008, Process.myUid(), context.packageName) as Int) {
            AppOpsManager.MODE_ALLOWED -> return true
            AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> return false
        }
    }
    return null
}

fun appInfoIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(context))

private fun granted(context: Context, perms: Array<String>) = perms.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

fun setupSteps(context: Context): List<SetupStep> = buildList {
    add(
        SetupStep(
            "sms", Icons.Default.Sms, "Send & receive SMS",
            "The core of everything — without it nothing is sent.",
            "Tap \"Allow\"",
            permissions = arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
        )
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(
            SetupStep(
                "notifications", Icons.Default.Notifications, "Notifications",
                "See what was sent, and approve AI replies.",
                "Tap \"Allow\"",
                permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            )
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(
            SetupStep(
                "exact_alarm", Icons.Default.Alarm, "Exact alarms",
                "Sends at 08:30 — not 08:41.",
                "Switch on \"Allow setting alarms and reminders\", then go back",
                intent = { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(it)) },
                check = { it.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true }
            )
        )
    }
    add(
        SetupStep(
            "battery", Icons.Default.BatteryFull, "Never sleep",
            "Stops the phone from pausing your macros to save battery.",
            if (isXiaomi()) "Choose \"No restrictions\", then go back" else "Tap \"Allow\"",
            intent = { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(it)) },
            check = {
                it.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(it.packageName) == true
            }
        )
    )
    if (isXiaomi()) {
        add(
            SetupStep(
                "xiaomi_autostart", Icons.Default.RocketLaunch, "Xiaomi: Autostart",
                "Xiaomi stops apps that aren't allowed to start by themselves.",
                "Find Automatiq and switch it on, then go back",
                check = { xiaomiAutostartEnabled(it) },
                confirmQuestion = "Did you switch Automatiq on under Autostart?",
                intent = {
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                }
            )
        )
        add(
            SetupStep(
                "xiaomi_perms", Icons.Default.Security, "Xiaomi: Extra permissions",
                "Xiaomi has its own SMS gate on top of Android's.",
                "Under \"Other permissions\": allow everything about SMS, then go back",
                confirmQuestion = "Did you allow the SMS permissions for Automatiq?",
                intent = {
                    Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.permissions.PermissionsEditorActivity"
                        )
                        .putExtra("extra_pkgname", it.packageName)
                }
            )
        )
    }
}

/** The step's state as far as the phone can tell; null = can't be read. */
private fun readStep(context: Context, step: SetupStep): Boolean? = when {
    step.permissions.isNotEmpty() -> granted(context, step.permissions)
    else -> step.check?.invoke(context)
}

fun isStepDone(context: Context, step: SetupStep): Boolean =
    readStep(context, step) ?: confirmed(context, step.id)

private fun confirmed(context: Context, id: String) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(id, false)

private fun setConfirmed(context: Context, id: String, value: Boolean) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(id, value).apply()

/** How many steps are still open — cheap enough to call on every resume. */
fun missingSetupSteps(context: Context): Int = setupSteps(context).count { !isStepDone(context, it) }

/** UI-facing state for a checklist plus the "fix everything" autopilot. */
@Stable
class SetupController internal constructor(
    val steps: List<SetupStep>,
    private val doneState: State<Map<String, Boolean>>,
    private val runningState: State<Boolean>,
    private val stuckState: State<String?>,
    private val justDoneState: State<String?>,
    private val confirmState: State<String?>,
    val fix: (SetupStep) -> Unit,
    val fixAll: () -> Unit,
    val openAppInfo: () -> Unit,
    /** Answer to [confirmStep]'s question. */
    val answerConfirm: (Boolean) -> Unit
) {
    val done: Map<String, Boolean> get() = doneState.value
    val doneCount: Int get() = done.count { it.value }
    val allDone: Boolean get() = doneCount == steps.size
    val running: Boolean get() = runningState.value
    /** A step the user came back from without completing — show help for it. */
    val stuck: String? get() = stuckState.value
    /** The step that just turned green — for a celebration beat. */
    val justDone: String? get() = justDoneState.value
    /** A step whose result the phone can't read — ask the user if they did it. */
    val confirmStep: SetupStep? get() = steps.firstOrNull { it.id == confirmState.value }
}

/**
 * Drives the checklist. Every step is evaluated when the user comes back to the app (ON_RESUME),
 * which covers both system screens and the runtime permission dialog. During "fix everything",
 * a completed step chains straight into the next open one; a step left undone stops the run and
 * is flagged so the row can explain the way around it (App info, restricted settings).
 */
@Composable
fun rememberSetupController(): SetupController {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val steps = remember { setupSteps(context) }

    val done = remember { mutableStateOf(steps.associate { it.id to isStepDone(context, it) }) }
    val running = remember { mutableStateOf(false) }
    val stuck = remember { mutableStateOf<String?>(null) }
    val justDone = remember { mutableStateOf<String?>(null) }
    val confirm = remember { mutableStateOf<String?>(null) }
    var awaiting by remember { mutableStateOf<String?>(null) }
    // When a permission result sends us straight on to App info, a resume may briefly happen in
    // between; it must not be judged as "the user came back". A real return takes seconds.
    var redirectedAt by remember { mutableLongStateOf(0L) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun openAppInfo() {
        runCatching { context.startActivity(appInfoIntent(context)) }
    }

    lateinit var launch: (SetupStep) -> Unit

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        // Android stops showing the dialog after two refusals; then App info is the only way in.
        if (denied.isNotEmpty() && activity != null &&
            denied.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        ) {
            val step = steps.firstOrNull { it.id == awaiting }
            redirectedAt = SystemClock.uptimeMillis()
            openAppInfo()
            toast("Tap Permissions → ${step?.title ?: "the permission"} → Allow, then go back")
        }
    }

    fun next() {
        val open = steps.firstOrNull { done.value[it.id] != true }
        if (open == null) {
            running.value = false
            return
        }
        launch(open)
    }

    launch = { step ->
        stuck.value = null
        awaiting = step.id
        if (step.permissions.isNotEmpty()) {
            permLauncher.launch(step.permissions)
        } else {
            val ok = runCatching { context.startActivity(step.intent!!.invoke(context)) }.isSuccess
            if (!ok) openAppInfo()
            toast(step.howTo)
        }
    }

    fun settle(id: String) {
        if (done.value[id] == true) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            justDone.value = id
            if (running.value) scope.launch {
                delay(700)
                next()
            }
        } else {
            stuck.value = id
            running.value = false
        }
    }

    fun answer(yes: Boolean) {
        val id = confirm.value ?: return
        confirm.value = null
        setConfirmed(context, id, yes)
        done.value = steps.associate { it.id to isStepDone(context, it) }
        settle(id)
    }

    fun evaluate() {
        if (SystemClock.uptimeMillis() - redirectedAt < 1500) return
        val id = awaiting
        awaiting = null
        val step = steps.firstOrNull { it.id == id }
        done.value = steps.associate { it.id to isStepDone(context, it) }
        if (step == null) return
        // The phone can't tell whether the user did it: ask, and carry on from the answer.
        if (readStep(context, step) == null && step.confirmQuestion != null) {
            confirm.value = step.id
            return
        }
        settle(step.id)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) evaluate() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    return remember {
        SetupController(
            steps = steps,
            doneState = done,
            runningState = running,
            stuckState = stuck,
            justDoneState = justDone,
            confirmState = confirm,
            fix = { launch(it) },
            fixAll = {
                running.value = true
                next()
            },
            openAppInfo = { openAppInfo() },
            answerConfirm = { answer(it) }
        )
    }
}
