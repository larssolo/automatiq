package com.vibeactions.ui.log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: LogViewModel = hiltViewModel()) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val filter by vm.statusFilter.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Log") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                actions = {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear") }
                }
            )
        }
    ) { p ->
        Column(Modifier.padding(p).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(filter == null, { vm.setFilter(null) }, { Text("All") })
                MacroStatus.entries.forEach { s ->
                    FilterChip(filter == s, { vm.setFilter(s) }, { Text(s.name) })
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs, key = { it.id }) { log ->
                    Column(Modifier.fillMaxWidth()) {
                        Row {
                            Text(log.status.name,
                                color = if (log.status == MacroStatus.SUCCESS) Primary else ErrorRed,
                                fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                modifier = Modifier.weight(1f))
                            Text(fmt.format(Date(log.triggeredAt)),
                                color = OnSurfaceVariant, fontSize = 12.sp)
                        }
                        Text(log.messagePreview ?: "", color = OnSurface, fontSize = 13.sp)
                        log.errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                        HorizontalDivider(color = Outline)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            title = { Text("Clear log?") },
            text = { Text("This deletes all log entries.") }
        )
    }
}
