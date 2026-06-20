package com.vibeactions.ui

import android.Manifest
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
import com.vibeactions.ui.common.ShaderGradientBackground
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.vibeactions.ui.common.PermissionBanner
import com.vibeactions.ui.editor.MacroEditorScreen
import com.vibeactions.ui.log.LogScreen
import com.vibeactions.ui.macrolist.MacroListScreen
import com.vibeactions.ui.settings.SettingsScreen
import com.vibeactions.ui.theme.VibeActionsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VibeActionsTheme { AppRoot() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current
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
        if (missing.isNotEmpty()) {
            smsPermLauncher.launch(missing.toTypedArray())
        }
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

    Box(Modifier.fillMaxSize()) {
        ShaderGradientBackground(Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Color(0xDD0D0D0D)) {
                    NavigationBarItem(
                        selected = route == "list", onClick = { nav.navigate("list") },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, "Macros") }, label = { Text("Macros") })
                    NavigationBarItem(
                        selected = route == "log", onClick = { nav.navigate("log") },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, "Log") }, label = { Text("Log") })
                    NavigationBarItem(
                        selected = route == "settings", onClick = { nav.navigate("settings") },
                        icon = { Icon(Icons.Default.Settings, "Settings") }, label = { Text("Settings") })
                }
            }
        ) { p ->
            NavHost(nav, startDestination = "list", modifier = Modifier.padding(p)) {
            composable("list") {
                MacroListScreen(
                    onNew = { nav.navigate("editor") },
                    onEdit = { id -> nav.navigate("editor?id=$id") },
                    banner = {
                        if (!smsGranted) {
                            PermissionBanner(
                                "SMS permission is required to send messages.",
                                "Grant"
                            ) {
                                smsPermLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                            }
                        }
                    }
                )
            }
            composable("editor") { MacroEditorScreen(macroId = null, onDone = { nav.popBackStack() }) }
            composable(
                "editor?id={id}",
                arguments = listOf(navArgument("id") { nullable = true; defaultValue = null })
            ) { entry ->
                MacroEditorScreen(macroId = entry.arguments?.getString("id"),
                    onDone = { nav.popBackStack() })
            }
            composable("log") { LogScreen() }
            composable("settings") { SettingsScreen() }
        }
        }
    }
}
