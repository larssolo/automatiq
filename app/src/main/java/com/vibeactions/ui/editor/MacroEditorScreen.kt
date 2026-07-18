package com.vibeactions.ui.editor

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
import com.vibeactions.domain.model.AiSendMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.util.CardColorPalette
import com.vibeactions.util.expandTemplate
import com.vibeactions.util.geminiSuggest
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
    onSend: (() -> Unit)? = null
) {
    LaunchedEffect(macroId) { vm.load(macroId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showExpiry by remember { mutableStateOf(false) }
    var intervalExpanded by remember { mutableStateOf(false) }
    var triggerExpanded by remember { mutableStateOf(false) }
    var aiModeExpanded by remember { mutableStateOf(false) }
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

    fun pickContact(index: Int) {
        pendingContactIndex = index
        val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) contactPickerLauncher.launch(null)
        else contactPermLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (macroId == null) "New Macro" else "Edit Macro") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                navigationIcon = { TextButton(onClick = onDone) { Text("Cancel") } },
                actions = { TextButton(enabled = s.canSave, onClick = { vm.save(onDone) }) { Text("Save") } }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { p ->
        Column(
            Modifier
                .padding(p)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            OutlinedTextField(
                value = s.name, onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text("Name") }, isError = s.name.isNotEmpty() && !s.nameValid,
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text("Widget color", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardColorPalette.forEach { swatch ->
                        val selected = s.cardColor == swatch
                        Box(
                            Modifier
                                .size(32.dp)
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

            ExposedDropdownMenuBox(
                expanded = triggerExpanded,
                onExpandedChange = { triggerExpanded = it }
            ) {
                OutlinedTextField(
                    value = triggerLabel(s.triggerType),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Trigger") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = triggerExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = triggerExpanded,
                    onDismissRequest = { triggerExpanded = false }
                ) {
                    TriggerType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(triggerLabel(type)) },
                            onClick = {
                                vm.update { it.copy(triggerType = type) }
                                triggerExpanded = false
                            }
                        )
                    }
                }
            }

            if (s.triggerType == TriggerType.SCHEDULED) {
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Time: ${s.scheduledTime}")
                }

                Text("Repeat on", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                // Single letters (Mon→Sun) so all seven fit across; fixed order disambiguates T/T, S/S.
                val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..7).forEach { day ->
                        val selected = s.daysOfWeek.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                vm.update {
                                    val next = if (selected) it.daysOfWeek - day else it.daysOfWeek + day
                                    it.copy(daysOfWeek = next)
                                }
                            },
                            label = {
                                Text(
                                    dayLabels[day - 1],
                                    maxLines = 1,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (!s.daysValid) {
                    Text(
                        "Pick at least one day",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = intervalExpanded,
                    onExpandedChange = { intervalExpanded = it }
                ) {
                    OutlinedTextField(
                        value = intervalLabel(s.weekInterval),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = intervalExpanded,
                        onDismissRequest = { intervalExpanded = false }
                    ) {
                        (1..4).forEach { n ->
                            DropdownMenuItem(
                                text = { Text(intervalLabel(n)) },
                                onClick = {
                                    vm.update { st ->
                                        st.copy(
                                            weekInterval = n,
                                            startEpochDay = if (n > 1 && st.startEpochDay == null)
                                                LocalDate.now().toEpochDay() else st.startEpochDay
                                        )
                                    }
                                    intervalExpanded = false
                                }
                            )
                        }
                    }
                }

                if (s.weekInterval > 1) {
                    val startDay = s.startEpochDay ?: LocalDate.now().toEpochDay()
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Starts: ${LocalDate.ofEpochDay(startDay)}")
                    }
                }

                // Optional expiry: after this date the macro stops firing.
                val expiry = s.validUntilEpochDay
                if (expiry == null) {
                    OutlinedButton(onClick = { showExpiry = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Set expiry date (optional)")
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(onClick = { showExpiry = true }, modifier = Modifier.weight(1f)) {
                            Text("Expires: ${LocalDate.ofEpochDay(expiry)}")
                        }
                        IconButton(onClick = { vm.update { it.copy(validUntilEpochDay = null) } }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear expiry")
                        }
                    }
                }
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

                Text("Radius: ${s.radiusMeters.toInt()} m", style = MaterialTheme.typography.labelLarge)
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
                            shape = SegmentedButtonDefaults.itemShape(i, options.size)
                        ) { Text(label) }
                    }
                }

                // Geofences only fire in the background with "Allow all the time" (Android 10+).
                val fineGranted = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val bgGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                // On Android 11+ background location can't be granted from an in-app dialog — the
                // request is silently denied and the user must pick "Allow all the time" in Settings.
                // Android 10 still allows the runtime dialog, so keep the launcher there.
                val bgPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* result reflected on next recomposition */ }
                if (!bgGranted) {
                    val needsSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    Text(
                        when {
                            !fineGranted -> "First allow location access above, then enable \"all the time\"."
                            needsSettings -> "Open Settings and set location to \"Allow all the time\" so this fires in the background."
                            else -> "For this to work in the background, allow location \"all the time\"."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        enabled = fineGranted,
                        onClick = {
                            if (needsSettings) {
                                ctx.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", ctx.packageName, null)
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } else {
                                bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        }
                    ) { Text(if (needsSettings) "Open location settings" else "Allow background location") }
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
                            shape = SegmentedButtonDefaults.itemShape(i, options.size)
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

            if (s.triggerType == TriggerType.INCOMING) {
                // Auto-reply: match on the incoming sender/keyword; reply goes back to the sender.
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
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI reply (Gemini)", modifier = Modifier.weight(1f), color = OnSurface)
                    ThemedSwitch(
                        checked = s.aiReplyEnabled,
                        onCheckedChange = { v -> vm.update { it.copy(aiReplyEnabled = v) } }
                    )
                }
                if (s.aiReplyEnabled) {
                    ExposedDropdownMenuBox(
                        expanded = aiModeExpanded,
                        onExpandedChange = { aiModeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (s.aiSendMode == AiSendMode.AUTO)
                                "Send automatically and notify" else "Approve before sending",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI sending", color = OnSurface) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aiModeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = aiModeExpanded,
                            onDismissRequest = { aiModeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Approve before sending") },
                                onClick = {
                                    vm.update { it.copy(aiSendMode = AiSendMode.APPROVE) }
                                    aiModeExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Send automatically and notify") },
                                onClick = {
                                    vm.update { it.copy(aiSendMode = AiSendMode.AUTO) }
                                    aiModeExpanded = false
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = s.aiReplyInstruction,
                        onValueChange = { v -> vm.update { it.copy(aiReplyInstruction = v) } },
                        label = { Text("How the AI should reply (optional)") },
                        placeholder = { Text("e.g.: Reply short and friendly, max 1 sentence") },
                        supportingText = {
                            Text("Controls tone and length. Leave blank to use the system prompt from Settings.")
                        },
                        minLines = 2, modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "The message field below is used as a fallback if Gemini doesn't respond.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text("Recipients", style = MaterialTheme.typography.labelLarge, color = OnSurface)
                s.recipients.forEachIndexed { index, number ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = number,
                            onValueChange = { v ->
                                vm.update { st ->
                                    st.copy(recipients = st.recipients.toMutableList().also { it[index] = v })
                                }
                            },
                            label = { Text("Number ${index + 1}") },
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
                            }) { Icon(Icons.Default.Close, contentDescription = "Remove recipient") }
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
            }

            OutlinedTextField(
                value = s.message, onValueChange = { v -> vm.update { it.copy(message = v) } },
                label = { Text(if (s.triggerType == TriggerType.INCOMING) "Reply message" else "Message") },
                supportingText = {
                    Text("${s.message.length} chars · variables: ${TEMPLATE_TOKENS.joinToString(" ")}")
                },
                minLines = 3, modifier = Modifier.fillMaxWidth()
            )
            // Live preview: show the message with {dato}/{tid}/{ugedag}/{navn} filled in, so the
            // user can see exactly what will be sent before saving. Only shown when a token is used.
            if (TEMPLATE_TOKENS.any { s.message.contains(it) }) {
                val preview = remember(s.message, s.name) {
                    expandTemplate(s.message, LocalDateTime.now(), s.name.ifBlank { "macro" })
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Preview (right now)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(preview, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showAiCompose = true }) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("AI write", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", modifier = Modifier.weight(1f), color = OnSurface)
                ThemedSwitch(
                    checked = s.enabled,
                    onCheckedChange = { v -> vm.update { it.copy(enabled = v) } }
                )
            }

            if (macroId != null && onSend != null) {
                HorizontalDivider()
                FilledTonalButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Send, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.triggerType == TriggerType.MANUAL) "Send now" else "Send now (test)")
                }
            }

            if (macroId != null && (onDelete != null || onCopy != null)) {
                HorizontalDivider()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (onCopy != null) {
                        OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Duplicate")
                        }
                    }
                    if (onDelete != null) {
                        Button(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                        ) {
                            Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete")
                        }
                    }
                }
            }
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
                                        Modifier.padding(12.dp),
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
                            val model = prefs.getString("gemini_model", com.vibeactions.util.DEFAULT_GEMINI_MODEL)
                                ?.ifBlank { com.vibeactions.util.DEFAULT_GEMINI_MODEL }
                                ?: com.vibeactions.util.DEFAULT_GEMINI_MODEL
                            aiState = AiState.Loading
                            scope.launch {
                                val result = runCatching { geminiSuggest(key, aiPrompt, aiStyle, model) }
                                aiState = result.getOrNull()
                                    ?.let { AiState.Suggestions(it) }
                                    ?: AiState.Err(result.exceptionOrNull()?.message ?: "Unknown error")
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

private fun intervalLabel(n: Int): String = if (n <= 1) "Every week" else "Every $n weeks"

private fun triggerLabel(type: TriggerType): String = when (type) {
    TriggerType.SCHEDULED -> "Scheduled"
    TriggerType.MANUAL -> "Manual"
    TriggerType.INCOMING -> "Auto-reply"
    TriggerType.LOCATION -> "Location"
    TriggerType.CHARGING -> "On charging"
    TriggerType.BLUETOOTH -> "On Bluetooth"
    TriggerType.WIFI -> "On Wi-Fi"
}
