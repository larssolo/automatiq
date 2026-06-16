package com.vibeactions.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.vibeactions.R

val DmSans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium)
)
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
)

val AppTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = DmSans),
        bodyMedium = bodyMedium.copy(fontFamily = DmSans),
        titleMedium = titleMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontFamily = DmSans)
    )
}
