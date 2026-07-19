package com.vibeactions.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.domain.model.GeofenceTransition
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.ui.theme.*
import com.vibeactions.util.accentColorFor
import com.vibeactions.util.formatRecurrence
import com.vibeactions.util.maskPhone

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MacroCard(
    macro: Macro,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onSend: () -> Unit,
    onToggle: (Boolean) -> Unit,
    veinColor: Color? = null,
    onMoveToFolder: (() -> Unit)? = null,
    onMoveToRoot: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    val accent = Color(accentColorFor(macro))
    var menuExpanded by remember { mutableStateOf(false) }

    // Organic press physics: the card gives slightly under the finger and springs back.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
        label = "cardPress"
    )

    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(LeafShape)
            .background(Surface.copy(alpha = BackgroundSetting.cardOpacity))
            // Accent light falls off across the card instead of tinting it flat.
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.13f), accent.copy(alpha = 0.02f))
                )
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = { menuExpanded = true }
            )
    ) {
        // The vein: a living accent edge that breathes while the macro is armed. A folder's
        // color (veinColor) overrides the macro's own accent when the macro is a member.
        val vein = breathingVeinColor(veinColor ?: accent, macro.enabled)
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(vein)
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                macro.name,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                color = OnSurface,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                compactSummary(macro),
                color = OnSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (macro.triggerType == TriggerType.INCOMING && macro.aiReplyEnabled) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.End
            ) {
                Surface(color = accent.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        "AI",
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
        // One-tap manual send. Reply macros (auto-reply / missed call) have no recipient list
        // of their own, so a manual send has no target — the button is hidden for those.
        if (macro.triggerType != TriggerType.INCOMING && macro.triggerType != TriggerType.MISSED_CALL) {
            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                IconButton(onClick = onSend) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send now",
                        tint = accent,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
        // Drag handle for reordering (long-press is now reserved for the actions menu).
        Box(
            Modifier.fillMaxHeight().padding(end = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Reorder",
                tint = OnSurfaceVariant,
                modifier = dragHandleModifier.padding(6.dp)
            )
        }

        // Long-press actions menu. Anchored to the card row; box keeps it positioned at the start.
        Box {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                offset = DpOffset(x = (-45).dp, y = 0.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; onDelete() }
                )
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = { menuExpanded = false; onCopy() }
                )
                DropdownMenuItem(
                    text = { Text("Send now") },
                    leadingIcon = { Icon(Icons.Default.Send, contentDescription = null) },
                    onClick = { menuExpanded = false; onSend() }
                )
                if (onMoveToFolder != null) DropdownMenuItem(
                    text = { Text("Move to folder…") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = { menuExpanded = false; onMoveToFolder() }
                )
                if (onMoveToRoot != null) DropdownMenuItem(
                    text = { Text("Move to root") },
                    leadingIcon = { Icon(Icons.Default.FolderOff, contentDescription = null) },
                    onClick = { menuExpanded = false; onMoveToRoot() }
                )
                DropdownMenuItem(
                    text = { Text(if (macro.enabled) "Disable" else "Enable") },
                    leadingIcon = {
                        Icon(
                            if (macro.enabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                    },
                    onClick = { menuExpanded = false; onToggle(!macro.enabled) }
                )
            }
        }
    }
}


private fun compactSummary(macro: Macro): String = when (macro.triggerType) {
    TriggerType.SCHEDULED -> buildString {
        append(macro.scheduledTime ?: "--:--")
        val days = formatRecurrence(macro.daysOfWeek, macro.weekInterval)
        if (days.isNotBlank()) append(" · $days")
    }
    TriggerType.INCOMING -> buildString {
        append("Auto-reply")
        if (!macro.matchSender.isNullOrBlank()) append(" · ${maskPhone(macro.matchSender)}")
    }
    TriggerType.LOCATION -> buildString {
        append(if (macro.geofenceTransition == GeofenceTransition.EXIT) "On departure" else "On arrival")
        macro.radiusMeters?.let { append(" · ${it.toInt()} m") }
    }
    TriggerType.MANUAL -> "Manual trigger"
    TriggerType.MISSED_CALL -> buildString {
        append("Missed call")
        if (!macro.matchSender.isNullOrBlank()) append(" · ${maskPhone(macro.matchSender)}")
    }
    TriggerType.CHARGING -> if (macro.triggerOnConnect) "When plugged in" else "When unplugged"
    TriggerType.BLUETOOTH -> buildString {
        append(if (macro.triggerOnConnect) "BT connect" else "BT disconnect")
        macro.triggerTargetLabel?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }
    TriggerType.WIFI -> buildString {
        append(if (macro.triggerOnConnect) "Wi-Fi connect" else "Wi-Fi disconnect")
        macro.triggerTargetLabel?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }
}
