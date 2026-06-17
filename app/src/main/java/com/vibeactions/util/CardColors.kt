package com.vibeactions.util

/** Material 200-level pastels that read well on the dark (#0D0D0D) card surface. */
val CardColorPalette: List<Long> = listOf(
    0xFFEF9A9AL, // Rose
    0xFFF48FB1L, // Pink
    0xFFCE93D8L, // Purple
    0xFF9FA8DAL, // Indigo
    0xFF80DEEAL, // Cyan
    0xFFA5D6A7L, // Green
    0xFFE6EE9CL, // Lime
    0xFFFFCC80L, // Orange
    0xFFFFAB91L, // Deep Orange
    0xFF80CBC4L, // Teal
)

fun randomCardColor(): Long = CardColorPalette.random()
