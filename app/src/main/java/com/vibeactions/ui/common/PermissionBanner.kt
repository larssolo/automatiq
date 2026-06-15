package com.vibeactions.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vibeactions.ui.theme.ErrorRed
import com.vibeactions.ui.theme.OnSurface

@Composable
fun PermissionBanner(text: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(ErrorRed.copy(alpha = 0.15f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = OnSurface, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(actionLabel, color = ErrorRed) }
    }
}
