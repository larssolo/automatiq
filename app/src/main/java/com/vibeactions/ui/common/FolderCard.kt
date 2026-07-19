package com.vibeactions.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.domain.model.Folder
import com.vibeactions.ui.theme.JetBrainsMono
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Surface

/**
 * Accordion folder card. The switch (and tapping the card) expands/collapses the folder —
 * green = open, amber = closed. Long-press = Enable/Disable all members, Rename, Delete;
 * the bulk enable/disable lives in that menu, mirroring the macro card's menu toggle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(
    folder: Folder,
    memberCount: Int,
    activeCount: Int,
    switchOn: Boolean,
    onClick: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    val accent = Color(folder.cardColor)
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(LeafShape)
            .background(Surface.copy(alpha = BackgroundSetting.cardOpacity))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.03f))
                )
            )
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
    ) {
        val vein = breathingVeinColor(accent, activeCount > 0)
        Box(Modifier.width(4.dp).fillMaxHeight().background(vein))

        Icon(
            Icons.Default.Folder, contentDescription = null, tint = accent,
            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 12.dp).size(20.dp)
        )
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                folder.name, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
                color = OnSurface, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (memberCount == 0) "Empty folder — drag macros in"
                else "$memberCount macros · $activeCount active",
                color = OnSurfaceVariant, fontSize = 12.sp, maxLines = 1
            )
        }
        // Open/close control: checked = expanded. Toggling routes through the same callback as
        // tapping the card, so both paths stay in sync with the persisted expanded state.
        Box(Modifier.fillMaxHeight().padding(end = 2.dp), contentAlignment = Alignment.Center) {
            ThemedSwitch(checked = folder.expanded, onCheckedChange = { onClick() })
        }
        Box(Modifier.fillMaxHeight().padding(end = 4.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.DragHandle, contentDescription = "Reorder", tint = OnSurfaceVariant,
                modifier = dragHandleModifier.padding(6.dp)
            )
        }
        Box {
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (memberCount > 0) {
                    DropdownMenuItem(
                        text = { Text(if (switchOn) "Disable all" else "Enable all") },
                        leadingIcon = {
                            Icon(
                                if (switchOn) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                        },
                        onClick = { menuExpanded = false; onToggleAll(!switchOn) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Delete folder") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}
