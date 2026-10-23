package com.vibeactions.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Upload
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.vibeactions.ui.common.ScreenHeader
import com.vibeactions.ui.editor.EditorSection
import com.vibeactions.ui.editor.Hint
import com.vibeactions.ui.editor.SwitchRow
import com.vibeactions.ui.theme.JetBrainsMono
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibeactions.BuildConfig
import com.vibeactions.data.AppSettings
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Primary
import com.vibeactions.ui.common.BackgroundSetting
import com.vibeactions.ui.common.rememberAiConsentGate
import com.vibeactions.ui.setup.isXiaomi
import com.vibeactions.util.GEMINI_API_KEY_URL
import com.vibeactions.util.GEMINI_MODELS
import com.vibeactions.util.formatMinuteOfDay
import com.vibeactions.util.geminiGenerate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel = hiltViewModel(),
    onOpenHealth: () -> Unit = {},
    onOpenSetup: () -> Unit = {},
    onShowGuide: () -> Unit = {}
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val aiConsentGate = rememberAiConsentGate()
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var apiKey by remember { mutableStateOf(vm.getApiKey()) }
    var savedKey by remember { mutableStateOf(vm.getApiKey()) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var systemPrompt by remember { mutableStateOf(vm.getSystemPrompt()) }
    var model by remember { mutableStateOf(vm.getModel()) }
    var modelExpanded by remember { mutableStateOf(false) }
    var apiTestResult by remember { mutableStateOf<String?>(null) }
    var apiTestLoading by remember { mutableStateOf(false) }
    var quietEnabled by remember { mutableStateOf(AppSettings.quietHoursEnabled(context)) }
    var quietStart by remember { mutableStateOf(AppSettings.quietStartMinute(context)) }
    var quietEnd by remember { mutableStateOf(AppSettings.quietEndMinute(context)) }
    var showQuietStart by remember { mutableStateOf(false) }
    var showQuietEnd by remember { mutableStateOf(false) }
    var includeKeyInBackup by remember { mutableStateOf(false) }
    // Saving a key switches AI on, so the user must first agree to text going to Google.
    fun withKeyConsent(action: () -> Unit) = if (apiKey.isBlank()) action() else aiConsentGate(action)

    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExport
        if (uri != null && content != null) {
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            }
        }
    }
    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) vm.importBackup(text) { result ->
                    val message = result.fold(
                        onSuccess = { "Imported $it macros" },
                        onFailure = { "Import failed: ${it.message?.take(80) ?: "invalid file"}" }
                    )
                    scope.launch { snackbar.showSnackbar(message) }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // The window no longer pans for the keyboard (adjustResize): shrink the scroll area
                // instead, so the focused field scrolls into view and the title stays put.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ScreenHeader("settings")

            // ── Phone: the things that keep macros firing ─────────────────────────────────
            EditorSection(Icons.Default.PhoneAndroid, "PHONE", Primary) {
                Column {
                    NavRow(
                        Icons.Default.Checklist, "Phone setup",
                        "SMS, notifications, exact alarms, battery" + if (isXiaomi()) ", Xiaomi" else "",
                        onOpenSetup
                    )
                    NavRow(Icons.Default.MonitorHeart, "Health check", "Next fire times and permission status", onOpenHealth)
                    NavRow(Icons.Default.School, "Intro guide", "Setup, permissions and AI key — step by step", onShowGuide)
                }
            }

            // ── Quiet hours ───────────────────────────────────────────────────────────────
            EditorSection(Icons.Default.Bedtime, "QUIET HOURS", Primary) {
                SwitchRow(
                    "Pause auto-replies at night",
                    "Auto-replies (incl. AI and missed-call texts) wait out the window and are sent right after it ends. Scheduled and manual sends still go through.",
                    quietEnabled
                ) { quietEnabled = it; AppSettings.setQuietHoursEnabled(context, it) }
                if (quietEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeTile("FROM", formatMinuteOfDay(quietStart), Modifier.weight(1f)) { showQuietStart = true }
                        TimeTile("TO", formatMinuteOfDay(quietEnd), Modifier.weight(1f)) { showQuietEnd = true }
                    }
                }
            }

            // ── AI ────────────────────────────────────────────────────────────────────────
            EditorSection(Icons.Default.AutoAwesome, "AI · GEMINI", Primary, trailing = {
                val on = savedKey.isNotBlank()
                Text(
                    if (on) "Key saved" else "Off",
                    color = if (on) Primary else OnSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini API key") },
                    placeholder = { Text("Paste your key here") },
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (apiKeyVisible) "Hide key" else "Show key"
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (apiKey.isBlank()) {
                    NavRow(
                        Icons.AutoMirrored.Filled.OpenInNew, "Get a free API key",
                        "Google AI Studio: sign in → \"Create API key\" → copy it and paste it above."
                    ) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_API_KEY_URL))) }
                }
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System prompt (optional)") },
                    placeholder = { Text("Reply in a friendly tone, keep it short") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        singleLine = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        GEMINI_MODELS.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = { model = m; modelExpanded = false }
                            )
                        }
                    }
                }
                Hint("Busy or out of free quota? AI falls back to another Gemini model automatically. Test key checks only this one.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            withKeyConsent {
                                vm.saveApiKey(apiKey); savedKey = apiKey.trim()
                                vm.saveSystemPrompt(systemPrompt)
                                vm.saveModel(model)
                                scope.launch { snackbar.showSnackbar("AI settings saved") }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            withKeyConsent {
                                vm.saveApiKey(apiKey); savedKey = apiKey.trim()
                                vm.saveModel(model)
                                apiTestResult = null
                                apiTestLoading = true
                                scope.launch {
                                    val result = runCatching {
                                        geminiGenerate(apiKey.trim(), "", "Reply with only the word OK", model.trim())
                                    }
                                    apiTestLoading = false
                                    apiTestResult = result.getOrNull()
                                        ?.let { "✅ Works: $it" }
                                        ?: "❌ Error: ${result.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        enabled = apiKey.isNotBlank() && !apiTestLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (apiTestLoading)
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else
                            Text("Test key")
                    }
                }
                apiTestResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("✅")) Primary else MaterialTheme.colorScheme.error)
                }
            }

            // ── Backup ────────────────────────────────────────────────────────────────────
            EditorSection(Icons.Default.Backup, "BACKUP", Primary) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.exportBackup(includeKeyInBackup) { json ->
                                pendingExport = json; createDoc.launch("automatiq-backup.json")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { openDoc.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                }
                Hint("A JSON file with your macros, folders and settings. Import adds the macros from a backup or a shared file.")
                SwitchRow(
                    "Include API key in backup",
                    "Off by default — the backup file would then contain your Gemini key in plain text.",
                    includeKeyInBackup
                ) { includeKeyInBackup = it }
            }

            // ── Appearance ────────────────────────────────────────────────────────────────
            EditorSection(Icons.Default.Palette, "APPEARANCE", Primary) {
                // Label · slider · value on one line, rows packed tight.
                Column {
                    AppearanceSlider("Hue", "${BackgroundSetting.hue.toInt()}°",
                        BackgroundSetting.hue, 0f..360f) { BackgroundSetting.setHue(context, it) }
                    AppearanceSlider("Saturation", "${(BackgroundSetting.saturation * 100).toInt()}%",
                        BackgroundSetting.saturation, 0f..2f) { BackgroundSetting.setSaturation(context, it) }
                    AppearanceSlider("Brightness", "${(BackgroundSetting.brightness * 100).toInt()}%",
                        BackgroundSetting.brightness, 0.3f..1.6f) { BackgroundSetting.setBrightness(context, it) }
                    AppearanceSlider("Cards", "${(BackgroundSetting.cardOpacity * 100).toInt()}%",
                        BackgroundSetting.cardOpacity, 0f..1f) { BackgroundSetting.setCardOpacity(context, it) }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.kodekid.app"))
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Automatiq ${BuildConfig.VERSION_NAME} · ", color = OnSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                Text("kodekid.app", color = Primary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        // Above the keyboard if it is open, so "saved" / errors are never hidden behind it.
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }

    if (showQuietStart) {
        val tps = rememberTimePickerState(
            initialHour = quietStart / 60, initialMinute = quietStart % 60, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showQuietStart = false },
            confirmButton = {
                TextButton(onClick = {
                    quietStart = tps.hour * 60 + tps.minute
                    AppSettings.setQuietWindow(context, quietStart, quietEnd)
                    showQuietStart = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showQuietStart = false }) { Text("Cancel") } },
            text = { TimePicker(state = tps) }
        )
    }
    if (showQuietEnd) {
        val tps = rememberTimePickerState(
            initialHour = quietEnd / 60, initialMinute = quietEnd % 60, is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showQuietEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    quietEnd = tps.hour * 60 + tps.minute
                    AppSettings.setQuietWindow(context, quietStart, quietEnd)
                    showQuietEnd = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showQuietEnd = false }) { Text("Cancel") } },
            text = { TimePicker(state = tps) }
        )
    }
}

@Composable
private fun AppearanceSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = OnSurface, fontSize = 14.sp, modifier = Modifier.width(92.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f).height(36.dp)
        )
        Text(
            valueText, color = OnSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
    }
}

/** A tappable row that opens another screen or page: icon, title, explanation, chevron. */
@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = Primary, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = OnSurface, style = MaterialTheme.typography.bodyLarge)
            Hint(subtitle)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = OnSurfaceVariant)
    }
}

/** One end of the quiet-hours window: big mono time, tap to change — like the editor's time. */
@Composable
private fun TimeTile(label: String, time: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, fontFamily = JetBrainsMono, fontSize = 11.sp, letterSpacing = 1.5.sp, color = OnSurfaceVariant)
        Text(time, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 28.sp, color = Color.White)
        Text("Tap to change", color = Primary, style = MaterialTheme.typography.labelSmall)
    }
}

