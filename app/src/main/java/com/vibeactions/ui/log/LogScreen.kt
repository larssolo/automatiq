package com.vibeactions.ui.log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.DeliveryStatus
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
    val macroOptions by vm.macroOptions.collectAsStateWithLifecycle()
    val selectedMacroId by vm.selectedMacroId.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var macroMenuExpanded by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }
    val selectedMacroName = macroOptions.firstOrNull { it.first == selectedMacroId }?.second

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
            if (macroOptions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = macroMenuExpanded,
                    onExpandedChange = { macroMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMacroName ?: "All macros",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Macro") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = macroMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = macroMenuExpanded,
                        onDismissRequest = { macroMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All macros") },
                            onClick = { vm.setMacroFilter(null); macroMenuExpanded = false }
                        )
                        macroOptions.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { vm.setMacroFilter(id); macroMenuExpanded = false }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs, key = { it.log.id }) { row ->
                    val log = row.log
                    Column(Modifier.fillMaxWidth()) {
                        Row {
                            Text(
                                buildString {
                                    append(log.status.name)
                                    append(" · ")
                                    append(row.macroName ?: "(deleted macro)")
                                },
                                color = when (log.status) {
                                    MacroStatus.SUCCESS -> Primary
                                    MacroStatus.PENDING -> Amber
                                    MacroStatus.FAILED -> ErrorRed
                                },
                                fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text(fmt.format(Date(log.triggeredAt)),
                                color = OnSurfaceVariant, fontSize = 12.sp)
                        }
                        Text(log.messagePreview ?: "", color = OnSurface, fontSize = 13.sp)
                        log.errorMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                        when (log.deliveryStatus) {
                            DeliveryStatus.DELIVERED ->
                                Text("Delivered ✓✓", color = Primary, fontSize = 12.sp)
                            DeliveryStatus.FAILED ->
                                Text("Not delivered", color = ErrorRed, fontSize = 12.sp)
                            null -> {}
                        }
                        HorizontalDivider(color = OutlineVariant)
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
