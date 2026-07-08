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
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Primary
import com.vibeactions.ui.common.BackgroundSetting
import com.vibeactions.util.GEMINI_MODELS
import com.vibeactions.util.geminiGenerate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
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
                if (text != null) vm.import(text) { result ->
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
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
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
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
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
            HorizontalDivider()
            Button(onClick = { vm.export { json -> pendingExport = json; createDoc.launch("automatiq-macros.json") } },
                modifier = Modifier.fillMaxWidth()) { Text("Export macros (JSON)") }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()) { Text("Import macros (JSON)") }

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
}
