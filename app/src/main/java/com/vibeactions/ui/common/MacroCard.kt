package com.vibeactions.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.domain.model.GeofenceTransition
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.ui.theme.*
import com.vibeactions.util.formatRecurrence
import com.vibeactions.util.maskPhone

@Composable
fun MacroCard(
    macro: Macro,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (macro.cardColor != 0L) androidx.compose.ui.graphics.Color(macro.cardColor) else Primary

    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .background(accent.copy(alpha = if (macro.cardColor != 0L) 0.07f else 0f))
            .clickable { onClick() }
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (macro.enabled) accent else Outline)
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
        Column(
            Modifier
                .fillMaxHeight()
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End
        ) {
            StatusDot(macro.lastStatus)
            if (macro.triggerType == TriggerType.INCOMING && macro.aiReplyEnabled) {
                Spacer(Modifier.height(4.dp))
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
    }
}

@Composable
private fun StatusDot(status: MacroStatus?) {
    val color = when (status) {
        MacroStatus.SUCCESS -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        MacroStatus.FAILED -> ErrorRed
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Box(
        Modifier
            .size(7.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
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
}
