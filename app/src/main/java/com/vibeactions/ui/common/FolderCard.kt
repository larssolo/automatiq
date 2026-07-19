package com.vibeactions.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.vibeactions.R
import com.vibeactions.domain.model.Folder
import com.vibeactions.ui.theme.JetBrainsMono
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Surface

/**
 * Accordion folder card with a physical folder silhouette: a raised accent tab over the top-left
 * ([FolderTabShape]) makes folders unmistakable next to the flat macro cards. Tap (or the chevron
 * button) expands/collapses; long-press = Enable/Disable all members, Rename, Delete.
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

    Column(
        modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(FolderTabShape)
            .background(Surface.copy(alpha = BackgroundSetting.cardOpacity))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.03f))
                )
            )
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
    ) {
        // The raised tab: a plain accent strip — the clip's silhouette cuts its rounded top-left
        // corner and slanted right edge, so it reads as a physical folder's tab.
        Box(
            Modifier
                .fillMaxWidth(TAB_WIDTH_FRACTION)
                .height(TAB_HEIGHT)
                .background(accent.copy(alpha = 0.45f))
        )
        Row(Modifier.fillMaxWidth().weight(1f)) {
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
        // Open/close control: a small round chevron button in the folder's accent — points right
        // when closed, rotates down when open (file-tree language). Same callback as tapping the
        // card, so both paths stay in sync with the persisted expanded state.
        val chevronRotation by animateFloatAsState(
            targetValue = if (folder.expanded) 90f else 0f,
            animationSpec = tween(200),
            label = "chevron"
        )
        Box(
            Modifier
                .align(Alignment.CenterVertically)
                .padding(end = 2.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_chevron),
                contentDescription = if (folder.expanded) "Collapse" else "Expand",
                tint = accent,
                modifier = Modifier.size(15.dp).rotate(chevronRotation)
            )
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
}

private const val TAB_WIDTH_FRACTION = 0.38f
private val TAB_HEIGHT = 12.dp

/**
 * Physical folder silhouette: a rounded tab raised over the top-left, a slanted tab edge down to
 * the body, then the app's leaf-cut corner language on the body (6dp top-end/bottom-start, 18dp
 * bottom-end). The tab width matches the accent strip FolderCard draws under the clip.
 */
private object FolderTabShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            val tabH = TAB_HEIGHT.toPx()
            val tabW = size.width * TAB_WIDTH_FRACTION
            val slant = 16.dp.toPx()
            val rTab = 8.dp.toPx()
            val rSmall = 6.dp.toPx()
            val rBig = 18.dp.toPx()
            val path = Path().apply {
                moveTo(0f, rTab)
                quadraticBezierTo(0f, 0f, rTab, 0f)
                lineTo(tabW - slant, 0f)
                lineTo(tabW, tabH)
                lineTo(size.width - rSmall, tabH)
                quadraticBezierTo(size.width, tabH, size.width, tabH + rSmall)
                lineTo(size.width, size.height - rBig)
                quadraticBezierTo(size.width, size.height, size.width - rBig, size.height)
                lineTo(rSmall, size.height)
                quadraticBezierTo(0f, size.height, 0f, size.height - rSmall)
                close()
            }
            return Outline.Generic(path)
        }
    }
}
