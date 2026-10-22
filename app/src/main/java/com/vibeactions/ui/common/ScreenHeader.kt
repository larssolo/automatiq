package com.vibeactions.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.ui.theme.JetBrainsMono
import com.vibeactions.ui.theme.OnSurface

/** The mono, lower-case screen title that the macro list's "automatiq" wordmark set — so Log and
 *  Settings read as the same app. [trailing] holds the screen's own summary and actions. */
@Composable
fun ScreenHeader(title: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(top = 12.dp, start = 6.dp, end = 6.dp)
    ) {
        Text(
            title,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            color = OnSurface
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}
