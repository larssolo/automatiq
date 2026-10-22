package com.vibeactions.ui.log

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.DeliveryStatus
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.ui.common.BackgroundSetting
import com.vibeactions.ui.common.LeafShape
import com.vibeactions.ui.common.ReportAiButton
import com.vibeactions.ui.common.ScreenHeader
import com.vibeactions.ui.editor.actionIcon
import com.vibeactions.ui.editor.triggerIcon
import com.vibeactions.ui.theme.*
import com.vibeactions.util.AiReportSource
import com.vibeactions.util.accentColorFor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val DayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val DayYearFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

private fun statusLabel(s: MacroStatus) = when (s) {
    MacroStatus.SUCCESS -> "Success"
    MacroStatus.FAILED -> "Failed"
    MacroStatus.PENDING -> "Pending"
}

private fun statusColor(s: MacroStatus) = when (s) {
    MacroStatus.SUCCESS -> Primary
    MacroStatus.PENDING -> Amber
    MacroStatus.FAILED -> ErrorRed
}

/** "TODAY", "YESTERDAY", then "MON 19 OCT" (year only when it isn't this year). */
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "TODAY"
    today.minusDays(1) -> "YESTERDAY"
    else -> day.format(if (day.year == today.year) DayFmt else DayYearFmt).uppercase(Locale.ENGLISH)
}

@Composable
fun LogScreen(vm: LogViewModel = hiltViewModel()) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val filter by vm.statusFilter.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val macroOptions by vm.macroOptions.collectAsStateWithLifecycle()
    val selectedMacroId by vm.selectedMacroId.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val total = counts[null] ?: 0

    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        ScreenHeader("log") {
            if (macroOptions.isNotEmpty()) MacroPicker(macroOptions, selectedMacroId, vm::setMacroFilter)
            if (total > 0) {
                IconButton(onClick = { confirmClear = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.DeleteSweep, contentDescription = "Clear log",
                        tint = OnSurfaceVariant, modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Status chips with counts; a status with nothing in it fades back.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterPill("All", counts[null] ?: 0, Primary, filter == null) { vm.setFilter(null) }
            MacroStatus.entries.forEach { s ->
                FilterPill(statusLabel(s), counts[s] ?: 0, statusColor(s), filter == s) { vm.setFilter(s) }
            }
        }

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (total == 0 && selectedMacroId == null) "All quiet." else "Nothing here.",
                        fontFamily = JetBrainsMono, fontSize = 16.sp, color = OnSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (total == 0 && selectedMacroId == null) "Everything Automatiq sends will land here."
                        else "No entries match this filter.",
                        color = OnSurfaceVariant, fontSize = 13.sp
                    )
                }
            }
        } else {
            val grouped = remember(logs) {
                logs.groupBy { Instant.ofEpochMilli(it.log.triggeredAt).atZone(zone).toLocalDate() }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                grouped.forEach { (day, rows) ->
                    item(key = "day-$day") { DayHeader(dayLabel(day, today), rows.size) }
                    items(rows, key = { it.log.id }) { row -> LogCard(row) }
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
            text = { Text("This wipes the whole history. Your macros stay untouched.") }
        )
    }
}

@Composable
private fun DayHeader(label: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, letterSpacing = 1.5.sp, color = Color(0xFFE6E6E6)
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = Color.White.copy(alpha = 0.12f))
        Spacer(Modifier.width(10.dp))
        Text("$count", fontFamily = JetBrainsMono, fontSize = 12.sp, color = OnSurfaceVariant)
    }
}

/** A status filter: label + count, lit in the status colour when chosen. */
@Composable
private fun FilterPill(label: String, count: Int, color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .height(34.dp)
            .alpha(if (count == 0 && !selected) 0.55f else 1f)
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.25f))
            .border(1.dp, if (selected) color else Color.White.copy(alpha = 0.14f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (selected) Color.White else OnSurface, fontSize = 13.sp, fontFamily = DmSans)
        Spacer(Modifier.width(6.dp))
        Text(
            "$count", fontFamily = JetBrainsMono, fontSize = 12.sp,
            color = if (selected) color else OnSurfaceVariant
        )
    }
}

@Composable
private fun MacroPicker(
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val name = options.firstOrNull { it.first == selectedId }?.second
    val shape = RoundedCornerShape(50)
    Box {
        Row(
            Modifier
                .height(34.dp)
                .widthIn(max = 180.dp)
                .clip(shape)
                .background(if (name != null) Primary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.25f))
                .border(1.dp, if (name != null) Primary else Color.White.copy(alpha = 0.14f), shape)
                .clickable { open = true }
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name ?: "All macros", color = OnSurface, fontSize = 13.sp, fontFamily = DmSans,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Pick macro", tint = OnSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("All macros") }, onClick = { onSelect(null); open = false })
            options.forEach { (id, n) ->
                DropdownMenuItem(text = { Text(n) }, onClick = { onSelect(id); open = false })
            }
        }
    }
}

/** One run of a macro, styled like its card in the list: the macro's colour, icon and name, with
 *  what was sent and how it went. Tap to show the whole message. */
@Composable
private fun LogCard(row: LogRow) {
    val log = row.log
    val macro = row.macro
    val accent = macro?.let { Color(accentColorFor(it)) } ?: OnSurfaceVariant
    var expanded by remember { mutableStateOf(false) }
    // "AI · model" = which model wrote it; any other note on a sent message is why the fixed
    // text was used (amber); otherwise it's the error (red).
    val aiModel = log.errorMessage?.takeIf { it.startsWith("AI · ") }?.removePrefix("AI · ")
    val note = log.errorMessage?.takeIf { aiModel == null }

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(LeafShape)
            .background(Surface.copy(alpha = BackgroundSetting.cardOpacity))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.13f), accent.copy(alpha = 0.02f))))
            .clickable { expanded = !expanded }
            .animateContentSize()
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(statusColor(log.status).copy(alpha = 0.8f)))
        Box(
            Modifier
                .padding(start = 12.dp, top = 12.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                when {
                    macro == null -> Icons.Default.History
                    macro.actionType == com.vibeactions.domain.model.ActionType.SMS -> triggerIcon(macro.triggerType)
                    else -> actionIcon(macro.actionType)
                },
                contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)
            )
        }
        Column(
            Modifier.weight(1f).padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.macroName ?: "Deleted macro",
                    fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    color = if (macro == null) OnSurfaceVariant else OnSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Instant.ofEpochMilli(log.triggeredAt).atZone(ZoneId.systemDefault()).format(TimeFmt),
                    fontFamily = JetBrainsMono, fontSize = 12.sp, color = OnSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusTag(statusLabel(log.status), statusColor(log.status))
                when (log.deliveryStatus) {
                    DeliveryStatus.DELIVERED -> StatusTag("Delivered", Primary, Icons.Default.DoneAll)
                    DeliveryStatus.FAILED -> StatusTag("Not delivered", ErrorRed)
                    null -> {}
                }
                if (aiModel != null) StatusTag(aiModel, Color(0xFFB39DDB), Icons.Default.AutoAwesome)
            }
            if (!log.messagePreview.isNullOrBlank()) {
                Text(
                    log.messagePreview, color = OnSurface, fontSize = 14.sp, lineHeight = 19.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis
                )
            }
            note?.let {
                Text(
                    it, fontSize = 12.sp, lineHeight = 16.sp,
                    color = if (log.status == MacroStatus.SUCCESS) Amber else ErrorRed,
                    maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (row.aiMacro && !log.messagePreview.isNullOrBlank()) {
                ReportAiButton(
                    log.messagePreview, AiReportSource.AUTO_REPLY,
                    modifier = Modifier.offset(x = (-12).dp).height(32.dp)
                )
            }
        }
    }
}

/** A small rounded tag: status, delivery report or the AI model that wrote the text. */
@Composable
private fun StatusTag(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, color = color, fontSize = 11.sp, fontFamily = JetBrainsMono, maxLines = 1)
    }
}
