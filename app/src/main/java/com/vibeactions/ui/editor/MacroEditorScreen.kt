package com.vibeactions.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import com.vibeactions.domain.model.AiSendMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.vibeactions.domain.model.GeofenceTransition
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.util.TEMPLATE_TOKENS
import com.vibeactions.util.isValidPhone
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    macroId: String?,
    onDone: () -> Unit,
    vm: MacroEditorViewModel = hiltViewModel()
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
        topBar = {
            TopAppBar(
                title = { Text(if (macroId == null) "New Macro" else "Edit Macro") },
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

                Text("Repeat on", style = MaterialTheme.typography.labelLarge)
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
                val bgGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                val bgPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* result reflected on next recomposition */ }
                if (!bgGranted) {
                    Text(
                        "For this to work in the background, allow location \"all the time\".",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        bgPermLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }) { Text("Allow background location") }
                }
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
                            Icon(Icons.Default.Person, contentDescription = "Pick contact")
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
                    Text("AI-svar (Gemini)", modifier = Modifier.weight(1f))
                    Switch(
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
                                "Send automatisk og informer" else "Godkend inden afsendelse",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI afsendelse") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aiModeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = aiModeExpanded,
                            onDismissRequest = { aiModeExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Godkend inden afsendelse") },
                                onClick = {
                                    vm.update { it.copy(aiSendMode = AiSendMode.APPROVE) }
                                    aiModeExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Send automatisk og informer") },
                                onClick = {
                                    vm.update { it.copy(aiSendMode = AiSendMode.AUTO) }
                                    aiModeExpanded = false
                                }
                            )
                        }
                    }
                    Text(
                        "Besked-feltet nedenfor bruges som fallback hvis Gemini ikke svarer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Text("Recipients", style = MaterialTheme.typography.labelLarge)
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
                            Icon(Icons.Default.Person, contentDescription = "Pick contact")
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
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { showAiCompose = true }) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("AI-skriv", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(checked = s.enabled, onCheckedChange = { v -> vm.update { it.copy(enabled = v) } })
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
        var aiLoading by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!aiLoading) showAiCompose = false },
            title = { Text("AI-skriv besked") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (aiLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("Genererer…", style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(
                            value = aiPrompt,
                            onValueChange = { aiPrompt = it },
                            label = { Text("Beskriv beskeden") },
                            placeholder = { Text("fx: venlig reminder om møde kl. 14") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = aiPrompt.isNotBlank() && !aiLoading,
                    onClick = {
                        val prefs = ctx.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
                        val key = prefs.getString("gemini_api_key", "") ?: ""
                        if (key.isBlank()) {
                            android.widget.Toast.makeText(
                                ctx, "Tilføj Gemini API-nøgle i Indstillinger", android.widget.Toast.LENGTH_SHORT
                            ).show()
                            showAiCompose = false
                            return@TextButton
                        }
                        aiLoading = true
                        val systemPrompt = prefs.getString("gemini_system_prompt", "") ?: ""
                        scope.launch {
                            val result = runCatching {
                                com.vibeactions.util.geminiGenerate(key, systemPrompt, aiPrompt)
                            }
                            aiLoading = false
                            result.getOrNull()?.let { generated ->
                                vm.update { it.copy(message = generated) }
                            }
                            showAiCompose = false
                        }
                    }
                ) { Text("Generer") }
            },
            dismissButton = {
                TextButton(onClick = { if (!aiLoading) showAiCompose = false }) { Text("Annuller") }
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
}
