package com.vibeactions.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (macro.enabled) Primary else Outline)
        )
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(macro.name, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
                    color = OnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
                StatusBadge(macro.lastStatus)
            }
            Spacer(Modifier.height(6.dp))
            Text(maskPhone(macro.recipientNumber), color = OnSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (macro.triggerType == TriggerType.SCHEDULED) {
                    Column {
                        Text(macro.scheduledTime ?: "--:--", fontFamily = JetBrainsMono,
                            color = OnSurface, fontSize = 20.sp)
                        Text(formatRecurrence(macro.daysOfWeek, macro.weekInterval),
                            color = OnSurfaceVariant, fontSize = 11.sp)
                    }
                } else {
                    TextButton(onClick = onTap) { Text("TRIGGER", color = Primary) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("Edit", color = OnSurfaceVariant) }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate macro", tint = OnSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete macro", tint = OnSurfaceVariant)
                }
                Switch(checked = macro.enabled, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = OnPrimary, checkedTrackColor = Primary))
            }
        }
        dragHandle()
    }
}
