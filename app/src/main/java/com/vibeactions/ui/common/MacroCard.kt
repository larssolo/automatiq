package com.vibeactions.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.ui.theme.*
import com.vibeactions.util.formatRecurrence
import com.vibeactions.util.maskPhone

@Composable
fun MacroCard(
    macro: Macro,
    onToggle: (Boolean) -> Unit,
    onTap: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: @Composable () -> Unit = {}
) {
    val accent = if (macro.cardColor != 0L) Color(macro.cardColor) else Primary
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .background(accent.copy(alpha = if (macro.cardColor != 0L) 0.07f else 0f))
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (macro.enabled) accent else Outline)
        )
        Column(Modifier.weight(1f).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {

            // Header: name + recipient | status | overflow menu
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(macro.name, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
                        color = OnSurface, fontSize = 16.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(maskPhone(macro.recipientNumber), color = OnSurfaceVariant, fontSize = 13.sp)
                }
                StatusBadge(macro.lastStatus)
                MacroMenu(onEdit = onEdit, onCopy = onCopy, onDelete = onDelete)
            }

            Spacer(Modifier.height(10.dp))

            // Footer: schedule (time + recurrence) or a Send action | enable switch
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (macro.triggerType == TriggerType.SCHEDULED) {
                    Column(Modifier.weight(1f)) {
                        Text(macro.scheduledTime ?: "--:--", fontFamily = JetBrainsMono,
                            color = OnSurface, fontSize = 20.sp)
                        Text(formatRecurrence(macro.daysOfWeek, macro.weekInterval),
                            color = OnSurfaceVariant, fontSize = 11.sp)
                    }
                } else {
                    FilledTonalButton(onClick = onTap) { Text("Send now") }
                    Spacer(Modifier.weight(1f))
                }
                Switch(
                    checked = macro.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary)
                )
            }
        }
        dragHandle()
    }
}

@Composable
private fun MacroMenu(onEdit: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Macro actions", tint = OnSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { open = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = { open = false; onCopy() }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
                onClick = { open = false; onDelete() }
            )
        }
    }
}
