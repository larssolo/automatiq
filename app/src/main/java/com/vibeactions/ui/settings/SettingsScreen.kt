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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibeactions.data.AppSettings
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Primary
import com.vibeactions.ui.common.BackgroundSetting
import com.vibeactions.ui.common.ThemedSwitch
import com.vibeactions.util.GEMINI_MODELS
import com.vibeactions.util.formatMinuteOfDay
import com.vibeactions.util.geminiGenerate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel(), onOpenHealth: () -> Unit = {}) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var apiKey by remember { mutableStateOf(vm.getApiKey()) }
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

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }) { p ->
        Column(
            Modifier
                .padding(p)
                .fillMaxSize()
                .background(Color(0x80000000))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                "Appearance",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                "Hue: ${BackgroundSetting.hue.toInt()}°",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
            Slider(
                value = BackgroundSetting.hue,
                onValueChange = { v -> BackgroundSetting.setHue(context, v) },
                valueRange = 0f..360f
            )
            Text(
                "Saturation: ${(BackgroundSetting.saturation * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
            Slider(
                value = BackgroundSetting.saturation,
                onValueChange = { v -> BackgroundSetting.setSaturation(context, v) },
                valueRange = 0f..2f
            )
            Text(
                "Card opacity: ${(BackgroundSetting.cardOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant
            )
            Slider(
                value = BackgroundSetting.cardOpacity,
                onValueChange = { v -> BackgroundSetting.setCardOpacity(context, v) },
                valueRange = 0f..1f
            )
            HorizontalDivider()

            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                headlineContent = { Text("Exact alarm permission") },
                supportingContent = { Text("Required for precise scheduled sends (Android 12+)") },
                trailingContent = {
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // Deep-link straight to this app's toggle instead of the full app list.
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }) { Text("Open") }
                }
            )
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                headlineContent = { Text("Battery optimisation") },
                supportingContent = { Text("Whitelist Automatiq so alarms are not delayed") },
                trailingContent = {
                    TextButton(onClick = {
                        // One-tap allow dialog (permission is declared) instead of making the user
                        // hunt for the app in the systemwide optimisation list.
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }) { Text("Open") }
                }
            )
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                headlineContent = { Text("Notification settings") },
                trailingContent = {
                    TextButton(onClick = {
                        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        context.startActivity(i)
                    }) { Text("Open") }
                }
            )
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                headlineContent = { Text("Health check") },
                supportingContent = { Text("Next fire times + permission status") },
                trailingContent = {
                    TextButton(onClick = onOpenHealth) { Text("Open") }
                }
            )
            HorizontalDivider()
            Text(
                "Quiet hours",
                style = MaterialTheme.typography.titleSmall,
                color = OnSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Pause auto-replies at night", color = OnSurface)
                    Text(
                        "Incoming auto-replies (incl. AI) are held during the window. Scheduled and manual sends still go through.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                ThemedSwitch(
                    checked = quietEnabled,
                    onCheckedChange = { quietEnabled = it; AppSettings.setQuietHoursEnabled(context, it) }
                )
            }
            if (quietEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showQuietStart = true }, modifier = Modifier.weight(1f)) {
                        Text("From ${formatMinuteOfDay(quietStart)}")
                    }
                    OutlinedButton(onClick = { showQuietEnd = true }, modifier = Modifier.weight(1f)) {
                        Text("To ${formatMinuteOfDay(quietEnd)}")
                    }
                }
            }

            HorizontalDivider()
            Button(
                onClick = {
                    vm.exportBackup(includeKeyInBackup) { json ->
                        pendingExport = json; createDoc.launch("automatiq-backup.json")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export backup (macros + settings)") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Include API key in backup", color = OnSurface)
                    Text(
                        "Off by default — the backup file would then contain your Gemini key in plain text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                ThemedSwitch(
                    checked = includeKeyInBackup,
                    onCheckedChange = { includeKeyInBackup = it }
                )
            }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()) { Text("Import backup / macros") }

            HorizontalDivider()
            Text(
                "AI (Gemini)",
                style = MaterialTheme.typography.titleSmall,
                color = OnSurface,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API key") },
                placeholder = { Text("Get one free at aistudio.google.com") },
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
                    supportingText = { Text("Getting 'quota 0' with your key? Try a different model.") },
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.saveApiKey(apiKey)
                        vm.saveSystemPrompt(systemPrompt)
                        vm.saveModel(model)
                        scope.launch { snackbar.showSnackbar("AI settings saved") }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
                OutlinedButton(
                    onClick = {
                        vm.saveApiKey(apiKey)
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
                    color = if (it.startsWith("✅")) androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.larssohl.dk"))
                        )
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Claude & ", color = OnSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                Text("www.larssohl.dk", color = Primary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
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
