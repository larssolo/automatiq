package com.vibeactions.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.ui.theme.Amber
import com.vibeactions.ui.theme.ErrorRed
import com.vibeactions.ui.theme.OnPrimary
import com.vibeactions.ui.theme.Primary

@Composable
fun StatusBadge(status: MacroStatus?) {
    if (status == null) return
    val color = when (status) {
        MacroStatus.SUCCESS -> Primary
        MacroStatus.FAILED -> ErrorRed
        MacroStatus.PENDING -> Amber
    }
    val textColor = if (status == MacroStatus.FAILED) Color.White else OnPrimary
    Text(
        text = status.name,
        color = textColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
