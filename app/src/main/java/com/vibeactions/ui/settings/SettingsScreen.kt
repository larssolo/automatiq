package com.vibeactions.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Primary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingExport by remember { mutableStateOf<String?>(null) }

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
                if (text != null) vm.import(text) { count ->
                    scope.launch { snackbar.showSnackbar("Imported $count macros") }
                }
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) }) { p ->
        Column(
            Modifier.padding(p).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            ListItem(
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
                headlineContent = { Text("Battery optimisation") },
                supportingContent = { Text("Whitelist VibeActions so alarms are not delayed") },
                trailingContent = {
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }) { Text("Open") }
                }
            )
            ListItem(
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
            Button(onClick = { vm.export { json -> pendingExport = json; createDoc.launch("vibeactions-macros.json") } },
                modifier = Modifier.fillMaxWidth()) { Text("Export macros (JSON)") }
            OutlinedButton(onClick = { openDoc.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()) { Text("Import macros (JSON)") }

            Spacer(Modifier.weight(1f))

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
