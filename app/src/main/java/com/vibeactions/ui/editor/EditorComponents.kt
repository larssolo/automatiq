package com.vibeactions.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.ui.common.ThemedSwitch
import com.vibeactions.ui.theme.JetBrainsMono
import com.vibeactions.ui.theme.OnSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SectionShape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
private val SectionBg = Color(0xFF26262A)
private val Muted = Color(0xFFB4B4B8)

/**
 * One block of the editor ("WHEN", "SEND TO", …): a raised card with a mono kicker in the macro's
 * own colour, so the form reads as a few clear steps instead of one long column of fields.
 */
@Composable
internal fun EditorSection(
    icon: ImageVector,
    kicker: String,
    accent: Color,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SectionShape)
            .background(SectionBg)
            .border(1.dp, Color.White.copy(alpha = 0.06f), SectionShape)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                kicker, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
                fontSize = 12.sp, letterSpacing = 1.5.sp, color = accent,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke(this)
        }
        content()
    }
}

/** A grid of icon tiles for picking one option (trigger, action, sound mode). Four per row. */
@Composable
internal fun <T> ChoiceTiles(
    options: List<Triple<T, ImageVector, String>>,
    selected: T,
    accent: Color,
    perRow: Int = 4,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, icon, label) ->
                    val on = value == selected
                    val bg by animateColorAsState(
                        if (on) accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.04f), label = "tile"
                    )
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier
                            .weight(1f)
                            // Fixed height: every tile is the same size whatever its label.
                            .height(58.dp)
                            .clip(shape)
                            .background(bg)
                            .border(if (on) 1.5.dp else 1.dp, if (on) accent else Color.White.copy(alpha = 0.08f), shape)
                            .clickable { onSelect(value) }
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(icon, null, tint = if (on) accent else Muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        FitText(label, color = if (on) Color.White else Muted)
                    }
                }
                // Keep tile widths equal on a short last row.
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** One line of label text that shrinks (down to 9sp) instead of wrapping, so a long label like
 *  "Missed call" can't make its tile taller than the rest — also with a larger system font. */
@Composable
private fun FitText(text: String, color: Color) {
    var size by remember(text) { mutableStateOf(12f) }
    Text(
        text, color = color, fontSize = size.sp, lineHeight = (size + 2).sp,
        maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
        onTextLayout = { if (it.didOverflowWidth && size > 9f) size -= 0.5f }
    )
}

/** Fold-out header for the less-used options; [summary] says what's on while it's closed. */
@Composable
internal fun MoreOptionsRow(open: Boolean, summary: String, accent: Color, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("More options", color = OnSurface, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary, color = Muted, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ExpandMore, if (open) "Hide options" else "Show options",
            tint = accent, modifier = Modifier.rotate(turn))
    }
}

/** Title + explanation on the left, switch on the right. */
@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = OnSurface, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        ThemedSwitch(checked = checked, width = 48.dp, height = 26.dp, onCheckedChange = onChange)
    }
}

/** Quiet explanatory text, readable on the dark card. */
@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = modifier)
}

/** The message as the recipient will see it, drawn as a chat bubble. */
@Composable
internal fun MessageBubble(text: String, accent: Color) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Text("Preview — right now", color = Muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp))
                .background(accent.copy(alpha = 0.28f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

/** Icon for a non-SMS action — shown on list cards where the action says more than the trigger. */
internal fun actionIcon(type: com.vibeactions.domain.model.ActionType): ImageVector = when (type) {
    com.vibeactions.domain.model.ActionType.SMS -> Icons.Default.Sms
    com.vibeactions.domain.model.ActionType.SOUND -> Icons.Default.VolumeUp
    com.vibeactions.domain.model.ActionType.REMINDER -> Icons.Default.NotificationsActive
    com.vibeactions.domain.model.ActionType.WEBHOOK -> Icons.Default.Link
}

internal fun triggerIcon(type: TriggerType): ImageVector = when (type) {
    TriggerType.SCHEDULED -> Icons.Default.Schedule
    TriggerType.MANUAL -> Icons.Default.TouchApp
    TriggerType.INCOMING -> Icons.Default.Reply
    TriggerType.LOCATION -> Icons.Default.Place
    TriggerType.CHARGING -> Icons.Default.BatteryChargingFull
    TriggerType.BLUETOOTH -> Icons.Default.Bluetooth
    TriggerType.WIFI -> Icons.Default.Wifi
    TriggerType.MISSED_CALL -> Icons.Default.PhoneMissed
}

private val DateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)

/** "Mon 19 Oct 2026" — friendlier than the ISO date the buttons used to show. */
internal fun prettyDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DateFmt)

/** Human names for the message variables; the token itself is what gets inserted. */
internal fun tokenLabel(token: String): String = when (token) {
    "{dato}" -> "Date"
    "{tid}" -> "Time"
    "{ugedag}" -> "Weekday"
    "{navn}" -> "Macro name"
    "{modtager}" -> "Recipient"
    "{afsender}" -> "Sender"
    else -> token
}

/** Segmented buttons lit in the macro's colour instead of Material's default grey. */
@Composable
internal fun segColors(accent: Color): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = accent.copy(alpha = 0.22f),
    activeContentColor = Color.White,
    activeBorderColor = accent,
    inactiveContainerColor = Color.Transparent,
    inactiveContentColor = Muted,
    inactiveBorderColor = Color.White.copy(alpha = 0.15f)
)
