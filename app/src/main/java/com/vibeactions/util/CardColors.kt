package com.vibeactions.util

import com.vibeactions.domain.model.Macro

/** Material 500-level colors — fully saturated so accents stay bold on the dark (#0D0D0D) surface. */
val CardColorPalette: List<Long> = listOf(
    0xFFF44336L, // Red
    0xFFE91E63L, // Pink
    0xFF9C27B0L, // Purple
    0xFF3F51B5L, // Indigo
    0xFF00BCD4L, // Cyan
    0xFF4CAF50L, // Green
    0xFFCDDC39L, // Lime
    0xFFFF9800L, // Orange
    0xFFFF5722L, // Deep Orange
    0xFF009688L, // Teal
)

fun randomCardColor(): Long = CardColorPalette.random()

/**
 * Every macro gets a bold accent color: the explicit [Macro.cardColor] if one was ever assigned,
 * otherwise a color derived deterministically from the macro's id — stable across recompositions
 * and app restarts, unlike picking a fresh random value on every read.
 */
fun accentColorFor(macro: Macro): Long =
    if (macro.cardColor != 0L) macro.cardColor
    else CardColorPalette[macro.id.hashCode().mod(CardColorPalette.size)]
