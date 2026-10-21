package com.vibeactions.ui.editor

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.DoNotDisturbOn
import com.vibeactions.ui.theme.Amber
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DoNotDisturbOff
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.vibeactions.BuildConfig
import com.vibeactions.ui.common.ReportAiButton
import com.vibeactions.ui.common.rememberAiConsentGate
import com.vibeactions.util.AiReportSource
import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.scheduler.MacroActionRunner
import com.vibeactions.domain.model.SoundMode
import com.vibeactions.domain.model.ActionType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.vibeactions.domain.model.GeofenceTransition
import com.vibeactions.domain.model.STATE_TRIGGERS
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.ui.common.ThemedSwitch
import com.vibeactions.ui.theme.ErrorRed
import com.vibeactions.ui.theme.JetBrainsMono
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.Primary
import com.vibeactions.util.CardColorPalette
import com.vibeactions.util.expandTemplate
import com.vibeactions.util.resolveGeminiModel
import com.vibeactions.util.buildVaryPrompt
import com.vibeactions.util.geminiGenerateWithFallback
import com.vibeactions.util.geminiSuggest
import com.vibeactions.util.SENDER_TOKEN
import com.vibeactions.util.TEMPLATE_TOKENS
import com.vibeactions.util.isValidPhone
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface AiState {
    object Idle : AiState
    object Loading : AiState
    data class Suggestions(val items: List<String>) : AiState
    data class Err(val msg: String) : AiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    macroId: String?,
    onDone: () -> Unit,
    vm: MacroEditorViewModel = hiltViewModel(),
    onDelete: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    onSend: (() -> Unit)? = null,
    onCancel: () -> Unit = onDone
) {
    LaunchedEffect(macroId) { vm.load(macroId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showOneOffDate by remember { mutableStateOf(false) }
    var showExpiry by remember { mutableStateOf(false) }
    var showAiCompose by remember { mutableStateOf(false) }
    // -1 = matchSender field; 0+ = recipient index
    var pendingContactIndex by remember { mutableStateOf<Int?>(null) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        val index = pendingContactIndex ?: return@rememberLauncherForActivityResult
        pendingContactIndex = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val contactId = uri.lastPathSegment ?: return@launch
            val cursor = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
            )
            val number = cursor?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: return@launch
            val cleaned = number.replace("\\s".toRegex(), "")
            withContext(Dispatchers.Main) {
                if (index == -1) {
                    vm.update { it.copy(matchSender = cleaned) }
                } else {
                    vm.update { st ->
                        st.copy(recipients = st.recipients.toMutableList()
                            .also { list -> if (index < list.size) list[index] = cleaned })
                    }
                }
            }
        }
    }
    val contactPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) contactPickerLauncher.launch(null) }

    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    // Missed-call macros need call-state access, and READ_CALL_LOG for the caller's number
    // (without it Android redacts the number and there is no one to text back).
    LaunchedEffect(s.triggerType) {
        if (s.triggerType == TriggerType.MISSED_CALL) {
            val missing = listOf(
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG
            ).filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) phonePermLauncher.launch(missing.toTypedArray())
        }
    }

    fun pickContact(index: Int) {
        pendingContactIndex = index
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) contactPickerLauncher.launch(null)
        else contactPermLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    val accent = androidx.compose.ui.graphics.Color(s.cardColor)
    val replyTrigger = s.triggerType == TriggerType.INCOMING || s.triggerType == TriggerType.MISSED_CALL
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (macroId == null) "New macro" else "Edit macro") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
                actions = {
                    Button(
                        enabled = s.canSave, onClick = { vm.save(onDone) },
                        contentPadding = PaddingValues(horizontal = 20.dp)
                    ) { Text("Save") }
                    // Duplicate/Delete are rare, so they live in an overflow menu instead of
                    // big buttons at the bottom of the form.
                    if (macroId != null && (onDelete != null || onCopy != null)) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, "More", tint = OnSurface)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (onCopy != null) DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                    onClick = { menuOpen = false; onCopy() }
                                )
                                if (onDelete != null) DropdownMenuItem(
                                    text = { Text("Delete", color = ErrorRed) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = ErrorRed) },
                                    onClick = { menuOpen = false; onDelete() }
                                )
                            }
                        }
                    } else Spacer(Modifier.width(12.dp))
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { p ->
        Column(
            Modifier
                .padding(p)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ── Name, colour and on/off ──────────────────────────────────────────────────
            EditorSection(Icons.Default.Label, "MACRO", accent, trailing = {
                Text(
                    if (s.enabled) "Active" else "Paused",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.width(8.dp))
                ThemedSwitch(
                    checked = s.enabled, width = 48.dp, height = 26.dp,
                    onCheckedChange = { v -> vm.update { it.copy(enabled = v) } }
                )
            }) {
                OutlinedTextField(
                    value = s.name, onValueChange = { v -> vm.update { it.copy(name = v) } },
                    placeholder = { Text("Name, e.g. Good morning, Mom") },
                    isError = s.name.isNotEmpty() && !s.nameValid,
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // Swatches share the width so all of them show as full circles on narrow phones too.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CardColorPalette.forEach { swatch ->
                        val selected = s.cardColor == swatch
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(if (selected) 0.dp else 3.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color(swatch))
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) OnSurface else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { vm.update { it.copy(cardColor = swatch) } },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── When ─────────────────────────────────────────────────────────────────────
            EditorSection(Icons.Default.Bolt, "WHEN", accent) {
                val triggers = TriggerType.entries.filter {
                    s.triggerType == it ||
                        (it != TriggerType.LOCATION || BuildConfig.LOCATION_TRIGGER) &&
                        (it !in STATE_TRIGGERS || BuildConfig.DEVICE_TRIGGERS)
                }
                ChoiceTiles(
                    options = triggers.map { Triple(it, triggerIcon(it), triggerLabel(it)) },
                    selected = s.triggerType, accent = accent
                ) { type -> vm.update { it.copy(triggerType = type) } }

                if (s.triggerType == TriggerType.SCHEDULED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The time is the heart of a scheduled macro: big, mono, tap to change.
                        Column(
                            Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                .clickable { showTime = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                s.scheduledTime, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
                                fontSize = 40.sp, color = androidx.compose.ui.graphics.Color.White
                            )
                            Text(
                                "Tap to change", color = accent,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        // Recurring (weekly rhythm) vs one-time (a single date). One-time reuses the
                        // recurring engine under the hood — see EditorState.toMacro.
                        SingleChoiceSegmentedButtonRow {
                            SegmentedButton(
                                selected = !s.oneOff,
                                onClick = { vm.update { it.copy(oneOff = false) } },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                colors = segColors(accent),
                                icon = {}
                            ) { Text("Repeat") }
                            SegmentedButton(
                                selected = s.oneOff,
                                onClick = {
                                    vm.update {
                                        it.copy(
                                            oneOff = true,
                                            oneOffEpochDay = it.oneOffEpochDay ?: LocalDate.now().toEpochDay()
                                        )
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                colors = segColors(accent),
                                icon = {}
                            ) { Text("Once") }
                        }
                    }

                    if (s.oneOff) {
                        val d = s.oneOffEpochDay ?: LocalDate.now().toEpochDay()
                        OutlinedButton(onClick = { showOneOffDate = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("On ${prettyDate(d)}")
                        }
                    } else {
                        // Single letters (Mon→Sun) so all seven fit across; fixed order disambiguates T/T, S/S.
                        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            (1..7).forEach { day ->
                                val selected = s.daysOfWeek.contains(day)
                                // Solid green/red so on/off reads at a glance on any background.
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) Primary else ErrorRed.copy(alpha = 0.85f))
                                        .clickable {
                                            vm.update {
                                                val next = if (selected) it.daysOfWeek - day else it.daysOfWeek + day
                                                it.copy(daysOfWeek = next)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        dayLabels[day - 1], fontWeight = FontWeight.Medium,
                                        color = if (selected) androidx.compose.ui.graphics.Color.Black
                                            else androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                        }
                        if (!s.daysValid) {
                            Text(
                                "Pick at least one day",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // The less-used scheduling knobs fold away; the summary says what's switched on.
                    val extras = buildList {
                        if (!s.oneOff && s.randomDaysEnabled) add("${s.randomDaysMin}–${s.randomDaysMax} random days")
                        if (!s.oneOff && s.weekInterval > 1) add(intervalLabel(s.weekInterval).lowercase())
                        if (!s.oneOff && s.validUntilEpochDay != null) add("until ${prettyDate(s.validUntilEpochDay!!)}")
                        if (s.randomSpreadEnabled) add("±${s.randomSpreadMinutes} min")
                    }
                    var moreOpen by remember { mutableStateOf(false) }
                    MoreOptionsRow(
                        open = moreOpen,
                        summary = extras.joinToString(" · ").ifEmpty { "Random days, every N weeks, end date, vary time" },
                        accent = accent
                    ) { moreOpen = !moreOpen }
                    androidx.compose.animation.AnimatedVisibility(moreOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (!s.oneOff) {
                                SwitchRow(
                                    "Random days",
                                    "Send on only some of the selected days — picked at random each week",
                                    s.randomDaysEnabled
                                ) { on -> vm.update { it.copy(randomDaysEnabled = on) } }
                                if (s.randomDaysEnabled) {
                                    val maxDays = s.daysOfWeek.size.coerceAtLeast(1)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Between", color = OnSurface)
                                        SmallNumberMenu(s.randomDaysMin, 1..maxDays) { v ->
                                            vm.update { it.copy(randomDaysMin = v, randomDaysMax = maxOf(v, it.randomDaysMax)) }
                                        }
                                        Text("and", color = OnSurface)
                                        SmallNumberMenu(s.randomDaysMax, 1..maxDays) { v ->
                                            vm.update { it.copy(randomDaysMax = v, randomDaysMin = minOf(v, it.randomDaysMin)) }
                                        }
                                        Text("days a week", color = OnSurface)
                                    }
                                    if (!s.randomDaysValid) {
                                        Text(
                                            "Select at least ${s.randomDaysMax} days above",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Repeat", color = OnSurface, style = MaterialTheme.typography.bodyLarge)
                                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                        (1..4).forEach { n ->
                                            SegmentedButton(
                                                selected = s.weekInterval == n,
                                                onClick = {
                                                    vm.update { st ->
                                                        st.copy(
                                                            weekInterval = n,
                                                            startEpochDay = if (n > 1 && st.startEpochDay == null)
                                                                LocalDate.now().toEpochDay() else st.startEpochDay
                                                        )
                                                    }
                                                },
                                                shape = SegmentedButtonDefaults.itemShape(n - 1, 4),
                                                colors = segColors(accent),
                                                icon = {}
                                            ) { Text(if (n == 1) "Weekly" else "${n} wk") }
                                        }
                                    }
                                }

                                if (s.weekInterval > 1) {
                                    val startDay = s.startEpochDay ?: LocalDate.now().toEpochDay()
                                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Counting from ${prettyDate(startDay)}")
                                    }
                                }

                                // Optional expiry: after this date the macro stops firing.
                                val expiry = s.validUntilEpochDay
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedButton(onClick = { showExpiry = true }, modifier = Modifier.weight(1f)) {
                                        Text(if (expiry == null) "Set end date (optional)" else "Ends ${prettyDate(expiry)}")
                                    }
                                    if (expiry != null) {
                                        IconButton(onClick = { vm.update { it.copy(validUntilEpochDay = null) } }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear end date",
                                                tint = OnSurface)
                                        }
                                    }
                                }
                            }

                            // Random send-time spread: fire within ±N minutes of the exact time so the send
                            // isn't recognisably on-the-dot every day. The field's visibility is tied to the
                            // switch, not to the value, so clearing the number doesn't collapse the input.
                            SwitchRow(
                                "Vary send time",
                                "Fire at a slightly different time each day",
                                s.randomSpreadEnabled
                            ) { on ->
                                vm.update {
                                    it.copy(
                                        randomSpreadEnabled = on,
                                        // Restore a sensible default when turning on after clearing to 0.
                                        randomSpreadMinutes = if (on && it.randomSpreadMinutes < 1) 5
                                            else it.randomSpreadMinutes
                                    )
                                }
                            }
                            if (s.randomSpreadEnabled) {
                                OutlinedTextField(
                                    // Show blank rather than "0" so the field can be cleared and retyped.
                                    value = if (s.randomSpreadMinutes == 0) "" else s.randomSpreadMinutes.toString(),
                                    onValueChange = { v ->
                                        val n = v.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0
                                        vm.update { it.copy(randomSpreadMinutes = n) }
                                    },
                                    label = { Text("± minutes") },
                                    isError = !s.randomSpreadValid,
                                    supportingText = {
                                        Text(
                                            if (!s.randomSpreadValid) "Enter at least 1 minute."
                                            else "Each send fires within ±${s.randomSpreadMinutes} min of ${s.scheduledTime}."
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true, modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                if (s.triggerType == TriggerType.MANUAL) {
                    Hint("Runs only when you start it — from the list or a home-screen widget.")
                }

                if (s.triggerType == TriggerType.LOCATION) {
                val fused = remember { LocationServices.getFusedLocationProviderClient(ctx) }
                fun fetchLocation() {
                    try {
                        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { loc ->
                                if (loc != null) vm.update {
                                    it.copy(latitude = loc.latitude, longitude = loc.longitude)
                                }
                            }
                    } catch (e: SecurityException) { /* permission not granted */ }
                }
                val locPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> if (granted) fetchLocation() }

                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) fetchLocation()
                        else locPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (s.latitude != null) "Update to my location" else "Use my location now")
                }
                if (s.latitude != null && s.longitude != null) {
                    Text(
                        "Point: %.5f, %.5f".format(s.latitude, s.longitude),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "No location set yet",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Radius", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                    Spacer(Modifier.weight(1f))
                    // The value in the app's mono number language — big and accent-lit, so the
                    // slider's current setting reads at a glance on the dark sheet.
                    Text(
                        "${s.radiusMeters.toInt()} m",
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        color = Primary
                    )
                }
                Slider(
                    value = s.radiusMeters,
                    onValueChange = { v -> vm.update { it.copy(radiusMeters = v) } },
                    valueRange = 100f..2000f, steps = 18
                )

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(
                        GeofenceTransition.ENTER to "On arrival",
                        GeofenceTransition.EXIT to "On departure"
                    )
                    options.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = s.geofenceTransition == value,
                            onClick = { vm.update { it.copy(geofenceTransition = value) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            colors = segColors(accent),
                            icon = {},
                        ) { Text(label) }
                    }
                }

                // Geofences only fire in the background with "Allow all the time" (Android 10+).
                // Held as STATE refreshed on resume: the values change in system Settings, outside
                // any Compose-visible event — a plain val kept showing the stale banner after the
                // user returned from granting the permission.
                fun hasFine() = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                fun hasBg() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                var fineGranted by remember { mutableStateOf(hasFine()) }
                var bgGranted by remember { mutableStateOf(hasBg()) }
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            fineGranted = hasFine()
                            bgGranted = hasBg()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                // On Android 11+ background location can't be granted from an in-app dialog — the
                // request is silently denied and the user must pick "Allow all the time" in Settings.
                // Android 10 still allows the runtime dialog, so keep the launcher there.
                val bgPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { bgGranted = hasBg() }
                fun openAppLocationSettings() {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", ctx.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                // First tap asks for foreground location; on grant it chains STRAIGHT into the
                // Settings page (Android 11+ can't grant "all the time" from an in-app dialog).
                // The button was previously disabled until fine location was granted — it looked
                // active on the dark theme and silently did nothing.
                val finePermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                    if (fineGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        openAppLocationSettings()
                    }
                }
                if (!bgGranted) {
                    val needsSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    Text(
                        when {
                            !fineGranted -> "Location access is needed first — the button walks you through it."
                            needsSettings -> "In Settings: Permissions → Location → \"Allow all the time\" — needed so this fires in the background."
                            else -> "For this to work in the background, allow location \"all the time\"."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = {
                            when {
                                !fineGranted -> finePermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                                needsSettings -> openAppLocationSettings()
                                else -> bgPermLauncher.launch(
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                )
                            }
                        }
                    ) {
                        Text(
                            when {
                                !fineGranted -> "Allow location"
                                needsSettings -> "Open location settings"
                                else -> "Allow background location"
                            }
                        )
                    }
                }
                }

                if (s.triggerType in STATE_TRIGGERS) {
                val onLabel: String
                val offLabel: String
                when (s.triggerType) {
                    TriggerType.CHARGING -> { onLabel = "When plugged in"; offLabel = "When unplugged" }
                    else -> { onLabel = "On connect"; offLabel = "On disconnect" }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(true to onLabel, false to offLabel)
                    options.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = s.triggerOnConnect == value,
                            onClick = { vm.update { it.copy(triggerOnConnect = value) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            colors = segColors(accent),
                            icon = {},
                        ) { Text(label) }
                    }
                }

                if (s.triggerType == TriggerType.BLUETOOTH) {
                    var btMenu by remember { mutableStateOf(false) }
                    var bonded by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
                    fun loadBonded() {
                        bonded = runCatching {
                            val mgr = ctx.getSystemService(android.bluetooth.BluetoothManager::class.java)
                            mgr?.adapter?.bondedDevices
                                ?.map { (it.name ?: it.address) to it.address } ?: emptyList()
                        }.getOrDefault(emptyList())
                        btMenu = true
                    }
                    val btPermLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted -> if (granted) loadBonded() }
                    Text(
                        if (s.triggerTarget.isBlank()) "Any Bluetooth device"
                        else "Device: ${s.triggerTargetLabel.ifBlank { s.triggerTarget }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Box {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val ok = ContextCompat.checkSelfPermission(
                                        ctx, Manifest.permission.BLUETOOTH_CONNECT
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (ok) loadBonded()
                                    else btPermLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                } else loadBonded()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Choose paired device") }
                        DropdownMenu(expanded = btMenu, onDismissRequest = { btMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Any device") },
                                onClick = {
                                    vm.update { it.copy(triggerTarget = "", triggerTargetLabel = "") }
                                    btMenu = false
                                }
                            )
                            bonded.forEach { (name, address) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        vm.update { it.copy(triggerTarget = address, triggerTargetLabel = name) }
                                        btMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (s.triggerType == TriggerType.WIFI) {
                    OutlinedTextField(
                        value = s.triggerTarget,
                        onValueChange = { v -> vm.update { it.copy(triggerTarget = v, triggerTargetLabel = v) } },
                        label = { Text("Wi-Fi network (SSID)") },
                        supportingText = { Text("Leave blank for any network. A disconnect can't match a specific SSID.") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    "While enabled, a quiet \"watching for triggers\" notification keeps this working in the background.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                }

                if (s.triggerType == TriggerType.MISSED_CALL) {
                OutlinedTextField(
                    value = s.matchSender,
                    onValueChange = { v -> vm.update { it.copy(matchSender = v) } },
                    label = { Text("From caller (optional)") },
                    supportingText = { Text("Leave blank to text every missed caller back") },
                    trailingIcon = {
                        IconButton(onClick = { pickContact(-1) }) {
                            Icon(Icons.Default.Person, contentDescription = "Pick contact", tint = OnSurface)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                }

                if (s.triggerType == TriggerType.INCOMING) {
                OutlinedTextField(
                    value = s.matchSender,
                    onValueChange = { v -> vm.update { it.copy(matchSender = v) } },
                    label = { Text("From sender (optional)") },
                    supportingText = { Text("Leave blank to reply to anyone") },
                    trailingIcon = {
                        IconButton(onClick = { pickContact(-1) }) {
                            Icon(Icons.Default.Person, contentDescription = "Pick contact", tint = OnSurface)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = s.matchKeyword,
                    onValueChange = { v -> vm.update { it.copy(matchKeyword = v) } },
                    label = { Text("If message contains (optional)") },
                    supportingText = { Text("Leave blank to reply to any message") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                }
            }

            // ── What it does ─────────────────────────────────────────────────────────────
            // Reply triggers always answer by SMS, so they don't get a choice.
            if (!replyTrigger) {
                EditorSection(Icons.Default.PlayArrow, "DO THIS", accent) {
                    ActionSection(s, vm, accent)
                }
            }

            // ── Recipients ───────────────────────────────────────────────────────────────
            if (!replyTrigger && s.effectiveAction == ActionType.SMS) {
                EditorSection(Icons.Default.Person, "SEND TO", accent) {
                s.recipients.forEachIndexed { index, number ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Resolve the contact name (and own-number hint) off-thread so the user can
                        // see who a number belongs to — e.g. spotting their own number in the list.
                        val contactLabel by produceState<String?>(null, number) {
                            value = vm.recipientLabel(number)
                        }
                        OutlinedTextField(
                            value = number,
                            onValueChange = { v ->
                                vm.update { st ->
                                    st.copy(recipients = st.recipients.toMutableList().also { it[index] = v })
                                }
                            },
                            label = { Text("Number ${index + 1}") },
                            supportingText = contactLabel?.let { { Text(it) } },
                            isError = number.isNotBlank() && !isValidPhone(number),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { pickContact(index) }) {
                            Icon(Icons.Default.Person, contentDescription = "Pick contact", tint = OnSurface)
                        }
                        if (s.recipients.size > 1) {
                            IconButton(onClick = {
                                vm.update { st ->
                                    st.copy(recipients = st.recipients.filterIndexed { i, _ -> i != index })
                                }
                            }) {
                                // Explicit tint: the default content color renders dark-on-dark
                                // here, leaving the remove-X nearly invisible.
                                Icon(Icons.Default.Close, contentDescription = "Remove recipient",
                                    tint = OnSurface)
                            }
                        }
                    }
                }
                TextButton(onClick = { vm.update { st -> st.copy(recipients = st.recipients + "") } }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add recipient")
                }
                if (!s.phoneValid && s.recipients.any { it.isNotBlank() }) {
                    Text(
                        "Enter at least one valid number",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // Safety hint: texting your own number is a common mistake (you receive your own send).
                val ownNumberWarning by produceState(false, s.recipients) {
                    value = vm.anyRecipientIsOwnNumber(s.recipients)
                }
                if (ownNumberWarning) {
                    Text(
                        "⚠ En af modtagerne er din egen telefon — du modtager selv beskeden.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // Heads-up before an accidental mass-send.
                val cleanCount = s.recipients.count { it.isNotBlank() }
                if (cleanCount >= 5) {
                    Text(
                        "Denne macro sender til $cleanCount modtagere.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                }
            } else if (replyTrigger) {
                Hint(
                    "The reply goes back to whoever " +
                        (if (s.triggerType == TriggerType.INCOMING) "texted you." else "called you."),
                    Modifier.padding(horizontal = 8.dp)
                )
            }

            // ── Message ──────────────────────────────────────────────────────────────────
            if (s.effectiveAction != ActionType.SOUND) {
                val tokens = if (replyTrigger) TEMPLATE_TOKENS + SENDER_TOKEN else TEMPLATE_TOKENS
                EditorSection(
                    Icons.Default.ChatBubbleOutline,
                    when {
                        replyTrigger -> "REPLY"
                        s.effectiveAction == ActionType.REMINDER -> "REMINDER TEXT"
                        s.effectiveAction == ActionType.WEBHOOK -> "REQUEST BODY"
                        else -> "MESSAGE"
                    },
                    accent,
                    trailing = if (s.effectiveAction == ActionType.SMS) {
                        {
                            TextButton(onClick = { showAiCompose = true }) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("AI write", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else null
                ) {
                    // Local TextFieldValue so a tapped variable lands at the cursor, not the end.
                    var field by remember { mutableStateOf(TextFieldValue(s.message)) }
                    LaunchedEffect(s.message) {
                        if (field.text != s.message) field = TextFieldValue(s.message, TextRange(s.message.length))
                    }
                    OutlinedTextField(
                        value = field,
                        onValueChange = { v -> field = v; vm.update { it.copy(message = v.text) } },
                        placeholder = {
                            Text(
                                if (s.effectiveAction == ActionType.WEBHOOK) "Optional, sent with POST"
                                else "Write the text…"
                            )
                        },
                        supportingText = { Text("${s.message.length} characters") },
                        minLines = 3, modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Tap to insert",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tokens.forEach { token ->
                            AssistChip(
                                onClick = {
                                    val sel = field.selection
                                    val text = field.text.replaceRange(sel.min, sel.max, token)
                                    field = TextFieldValue(text, TextRange(sel.min + token.length))
                                    vm.update { it.copy(message = text) }
                                },
                                label = { Text(tokenLabel(token), style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    // Live preview: show the message with {dato}/{tid}/{ugedag}/{navn} filled in, so the
                    // user can see exactly what will be sent before saving. Only shown when a token is used.
                    if ((TEMPLATE_TOKENS + SENDER_TOKEN).any { s.message.contains(it) }) {
                        // Resolve the first recipient's name so {modtager} previews as a real name, not a token.
                        val firstRecipient = s.recipients.firstOrNull { it.isNotBlank() }?.trim()
                        val sampleRecipient by produceState(firstRecipient, firstRecipient) {
                            value = firstRecipient?.let { vm.recipientName(it) }
                        }
                        val preview = remember(s.message, s.name, replyTrigger, sampleRecipient) {
                            // Reply macros fill {afsender} with the counterparty; show a sample number so the
                            // preview isn't a literal {afsender}. Non-reply macros have no counterparty.
                            val sampleSender = if (replyTrigger) "12345678" else null
                            expandTemplate(
                                s.message, LocalDateTime.now(), s.name.ifBlank { "macro" },
                                sender = sampleSender, recipientName = sampleRecipient
                            )
                        }
                        MessageBubble(preview, accent)
                    }
                }
            }

            // ── AI ───────────────────────────────────────────────────────────────────────
            val aiAvailable = s.triggerType == TriggerType.INCOMING ||
                (s.triggerType == TriggerType.SCHEDULED && s.effectiveAction == ActionType.SMS)
            if (aiAvailable) {
                val isReply = s.triggerType == TriggerType.INCOMING
                EditorSection(Icons.Default.AutoAwesome, if (isReply) "AI REPLY" else "AI VARIATION", accent, trailing = {
                    ThemedSwitch(
                        checked = s.aiReplyEnabled, width = 48.dp, height = 26.dp,
                        onCheckedChange = { v -> vm.update { it.copy(aiReplyEnabled = v) } }
                    )
                }) {
                    Hint(
                        if (isReply) "Gemini writes each reply. The text above is sent if Gemini doesn't answer."
                        // AI variation: every fire sends a fresh Gemini rewrite of the message above, so a
                        // recurring send never delivers the exact same text twice.
                        else "Every send gets a fresh rewrite of your message, so it never reads the same twice. " +
                            "If Gemini doesn't answer, your text is sent unchanged."
                    )
                    if (s.aiReplyEnabled) {
                        AiSendModeField(s.aiSendMode, accent) { mode -> vm.update { it.copy(aiSendMode = mode) } }
                        OutlinedTextField(
                            value = s.aiReplyInstruction,
                            onValueChange = { v -> vm.update { it.copy(aiReplyInstruction = v) } },
                            label = { Text(if (isReply) "How to reply (optional)" else "Tone and style (optional)") },
                            placeholder = {
                                Text(if (isReply) "e.g. short and friendly, max 1 sentence" else "e.g. warm and casual, keep it short")
                            },
                            supportingText = if (isReply) {
                                { Text("Blank = the system prompt from Settings.") }
                            } else null,
                            minLines = 2, modifier = Modifier.fillMaxWidth()
                        )
                        // Try the AI now: generate one sample variation so you can see what kind of text
                        // it produces before enabling AUTO mode — without sending anything.
                        if (!isReply) AiVariationPreview(message = s.message, instruction = s.aiReplyInstruction)
                    }
                }
            }

            // Reply macros (auto-reply / missed call) send to the incoming counterparty, so a manual
            // test-send has no recipient and would be a silent no-op — hide it for those.
            if (macroId != null && onSend != null && !replyTrigger) {
                FilledTonalButton(
                    onClick = onSend,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Send, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            s.effectiveAction != ActionType.SMS -> "Run now"
                            s.triggerType == TriggerType.MANUAL -> "Send now"
                            else -> "Send now (test)"
                        }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTime) {
        val parts = s.scheduledTime.split(":")
        val tps = rememberTimePickerState(
            initialHour = parts[0].toInt(), initialMinute = parts[1].toInt(), is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.update { it.copy(scheduledTime = "%02d:%02d".format(tps.hour, tps.minute)) }
                    showTime = false
                }) { Text("OK") }
            },
            text = { TimePicker(state = tps) }
        )
    }

    if (showDate) {
        val initMs = (s.startEpochDay ?: LocalDate.now().toEpochDay()) * 86_400_000L
        val dps = rememberDatePickerState(initialSelectedDateMillis = initMs)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dps.selectedDateMillis?.let { ms ->
                        vm.update { it.copy(startEpochDay = ms / 86_400_000L) }
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = dps) }
    }

    if (showOneOffDate) {
        val initMs = (s.oneOffEpochDay ?: LocalDate.now().toEpochDay()) * 86_400_000L
        val dps = rememberDatePickerState(initialSelectedDateMillis = initMs)
        DatePickerDialog(
            onDismissRequest = { showOneOffDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dps.selectedDateMillis?.let { ms ->
                        vm.update { it.copy(oneOffEpochDay = ms / 86_400_000L) }
                    }
                    showOneOffDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showOneOffDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = dps) }
    }

    if (showExpiry) {
        val initMs = (s.validUntilEpochDay ?: LocalDate.now().toEpochDay()) * 86_400_000L
        val dps = rememberDatePickerState(initialSelectedDateMillis = initMs)
        DatePickerDialog(
            onDismissRequest = { showExpiry = false },
            confirmButton = {
                TextButton(onClick = {
                    dps.selectedDateMillis?.let { ms ->
                        vm.update { it.copy(validUntilEpochDay = ms / 86_400_000L) }
                    }
                    showExpiry = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showExpiry = false }) { Text("Cancel") } }
        ) { DatePicker(state = dps) }
    }

    if (showAiCompose) {
        val aiConsentGate = rememberAiConsentGate()
        var aiPrompt by remember { mutableStateOf("") }
        var aiStyle  by remember { mutableStateOf("") }
        var aiState  by remember { mutableStateOf<AiState>(AiState.Idle) }

        AlertDialog(
            onDismissRequest = { if (aiState !is AiState.Loading) showAiCompose = false },
            title = {
                Text(if (aiState is AiState.Suggestions) "Choose a message" else "AI-write message")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (val st = aiState) {
                        AiState.Idle -> {
                            OutlinedTextField(
                                value = aiPrompt,
                                onValueChange = { aiPrompt = it },
                                label = { Text("Describe the message") },
                                placeholder = { Text("e.g.: friendly reminder about the 2pm meeting") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = aiStyle,
                                onValueChange = { aiStyle = it },
                                label = { Text("Style/tone (optional)") },
                                placeholder = { Text("e.g.: short and casual") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        AiState.Loading -> {
                            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                            Text("Generating suggestions…", style = MaterialTheme.typography.bodySmall)
                        }
                        is AiState.Suggestions -> {
                            st.items.forEach { suggestion ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            suggestion,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        FilledTonalButton(onClick = {
                                            vm.update { it.copy(message = suggestion) }
                                            showAiCompose = false
                                        }) { Text("Choose") }
                                    }
                                    ReportAiButton(
                                        suggestion, AiReportSource.COMPOSE,
                                        Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }
                        is AiState.Err -> {
                            Text(
                                st.msg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                when (aiState) {
                    AiState.Idle -> TextButton(
                        enabled = aiPrompt.isNotBlank(),
                        onClick = {
                            val prefs = ctx.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
                            val key = prefs.getString("gemini_api_key", "") ?: ""
                            if (key.isBlank()) {
                                aiState = AiState.Err("No API key — add one in Settings")
                                return@TextButton
                            }
                            val model = resolveGeminiModel(prefs.getString("gemini_model", null))
                            aiConsentGate {
                                aiState = AiState.Loading
                                scope.launch {
                                    val result = runCatching { geminiSuggest(key, aiPrompt, aiStyle, model) }
                                    aiState = result.getOrNull()
                                        ?.let { AiState.Suggestions(it) }
                                        ?: AiState.Err(result.exceptionOrNull()?.message ?: "Unknown error")
                                }
                            }
                        }
                    ) { Text("Generate") }
                    is AiState.Suggestions, is AiState.Err -> TextButton(
                        onClick = { aiState = AiState.Idle }
                    ) { Text("Try again") }
                    AiState.Loading -> { /* no button while loading */ }
                }
            },
            dismissButton = {
                TextButton(onClick = { if (aiState !is AiState.Loading) showAiCompose = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Shared APPROVE/AUTO picker for AI macros — auto-replies and scheduled variations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSendModeField(mode: AiSendMode, accent: androidx.compose.ui.graphics.Color, onModeChange: (AiSendMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(AiSendMode.APPROVE to "Ask me first", AiSendMode.AUTO to "Send automatically")
                .forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = mode == value,
                        onClick = { onModeChange(value) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        colors = segColors(accent),
                        icon = {}
                    ) { Text(label) }
                }
        }
        Hint(
            if (mode == AiSendMode.APPROVE)
                "A notification lets you review, edit or discard the text before it goes out."
            else "The AI text is sent right away and you get a notification with what was sent."
        )
    }
}

/** A "try it now" button that generates one sample AI variation of [message] and shows it inline,
 *  so the user can judge the AI's output before turning on AUTO mode. Sends nothing. */
@Composable
private fun AiVariationPreview(message: String, instruction: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<AiState>(AiState.Idle) }
    val aiConsentGate = rememberAiConsentGate()
    OutlinedButton(
        onClick = { aiConsentGate {
            state = AiState.Loading
            scope.launch {
                val prefs = ctx.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
                val key = prefs.getString("gemini_api_key", "").orEmpty()
                if (key.isBlank()) {
                    state = AiState.Err("No API key — add one in Settings.")
                    return@launch
                }
                val model = resolveGeminiModel(prefs.getString("gemini_model", null))
                val body = expandTemplate(message, LocalDateTime.now(), "macro")
                val res = withContext(Dispatchers.IO) {
                    runCatching {
                        geminiGenerateWithFallback(key, buildVaryPrompt(instruction.ifBlank { null }), body, model, maxOutputTokens = 300).text
                    }
                }
                state = res.fold(
                    onSuccess = { AiState.Suggestions(listOf(it.trim())) },
                    onFailure = { AiState.Err(it.message?.take(120) ?: "Gemini error") }
                )
            }
        } },
        enabled = message.isNotBlank() && state != AiState.Loading,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (state == AiState.Loading) "Generating…" else "Preview AI variation")
    }
    when (val st = state) {
        is AiState.Suggestions -> Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "AI example", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(st.items.first(), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                ReportAiButton(st.items.first(), AiReportSource.PREVIEW)
            }
        }
        is AiState.Err -> Text(
            st.msg, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        else -> {}
    }
}

private fun intervalLabel(n: Int): String = if (n <= 1) "Every week" else "Every $n weeks"

private fun triggerLabel(type: TriggerType): String = when (type) {
    TriggerType.SCHEDULED -> "Scheduled"
    TriggerType.MANUAL -> "Manual"
    TriggerType.INCOMING -> "Auto-reply"
    TriggerType.LOCATION -> "Location"
    TriggerType.CHARGING -> "Charging"
    TriggerType.BLUETOOTH -> "Bluetooth"
    TriggerType.WIFI -> "Wi-Fi"
    TriggerType.MISSED_CALL -> "Missed call"
}

/** A compact number button that opens a menu of [range]. */
@Composable
private fun SmallNumberMenu(value: Int, range: IntRange, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
            Text("$value")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            range.forEach { n ->
                DropdownMenuItem(text = { Text("$n") }, onClick = { onPick(n); open = false })
            }
        }
    }
}

private val ACTION_OPTIONS = listOf(
    Triple(ActionType.SMS, Icons.Default.Sms, "Send SMS"),
    Triple(ActionType.SOUND, Icons.Default.VolumeUp, "Sound mode"),
    Triple(ActionType.REMINDER, Icons.Default.NotificationsActive, "Reminder"),
    Triple(ActionType.WEBHOOK, Icons.Default.Link, "Webhook")
)

private fun soundIcon(mode: SoundMode) = when (mode) {
    SoundMode.NORMAL -> Icons.Default.VolumeUp
    SoundMode.VIBRATE -> Icons.Default.Vibration
    SoundMode.SILENT -> Icons.Default.VolumeOff
    SoundMode.DND_ON -> Icons.Default.DoNotDisturbOn
    SoundMode.DND_OFF -> Icons.Default.DoNotDisturbOff
}

/** Action picker plus the settings of the chosen non-SMS action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSection(s: EditorState, vm: MacroEditorViewModel, accent: androidx.compose.ui.graphics.Color) {
    val ctx = LocalContext.current
    ChoiceTiles(ACTION_OPTIONS, s.actionType, accent) { type -> vm.update { it.copy(actionType = type) } }
    when (s.actionType) {
        ActionType.SOUND -> {
            Text("Switch the phone to", color = OnSurface, style = MaterialTheme.typography.bodyLarge)
            ChoiceTiles(
                SoundMode.entries.map { Triple(it, soundIcon(it), MacroActionRunner.soundModeLabel(it)) },
                s.soundMode, accent, perRow = 3
            ) { m -> vm.update { it.copy(soundMode = m) } }
            if (MacroActionRunner.needsDndAccess(s.soundMode)) {
                // Re-checked on resume: the user grants it in system settings and comes back.
                var granted by remember { mutableStateOf(dndGranted(ctx)) }
                val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                        if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) granted = dndGranted(ctx)
                    }
                    owner.lifecycle.addObserver(obs)
                    onDispose { owner.lifecycle.removeObserver(obs) }
                }
                if (!granted) {
                    DndAccessCard {
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                        Toast.makeText(ctx, "Find Automatiq and allow it, then go back", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        ActionType.WEBHOOK -> {
            OutlinedTextField(
                value = s.webhookUrl,
                onValueChange = { v -> vm.update { it.copy(webhookUrl = v) } },
                label = { Text("URL") },
                placeholder = { Text("https://…") },
                isError = s.webhookUrl.isNotBlank() && !s.webhookValid,
                supportingText = { Text("Called when the macro fires. Variables like {dato} work here too.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("GET", "POST").forEachIndexed { i, method ->
                    SegmentedButton(
                        selected = s.webhookMethod == method,
                        onClick = { vm.update { it.copy(webhookMethod = method) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        colors = segColors(accent),
                        icon = {}
                    ) { Text(method) }
                }
            }
        }
        ActionType.REMINDER -> Hint("Shows a notification with the text below when the macro fires.")
        ActionType.SMS -> {}
    }
}

private fun dndGranted(ctx: android.content.Context) =
    ctx.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted

/** "Needs permission" card in the style of the Phone setup rows, with a pulsing amber edge so it
 *  stands apart from the editor's ordinary buttons. The whole card is the tap target. */
@Composable
private fun DndAccessCard(onAllow: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "dnd").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "edge"
    )
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Amber.copy(alpha = 0.10f))
            .border(1.5.dp, Amber.copy(alpha = pulse.value), shape)
            .clickable(onClick = onAllow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Amber.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.DoNotDisturbOn, null, tint = Amber, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Do Not Disturb access", color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                "Needed to switch to this mode",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "ALLOW", color = Primary, fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.sp
        )
    }
}
