package com.vibeactions.domain.model

/** A named group of macros shown as an accordion card in the list. Pure organization: the firing
 *  engine never reads folder membership. */
data class Folder(
    val id: String,
    val name: String,
    /** ARGB accent from CardColorPalette, chosen at creation. */
    val cardColor: Long,
    /** Shares one ordering space with root (folderless) macros. */
    val sortOrder: Int = 0,
    /** Accordion state; persisted so the list looks the same after restart. */
    val expanded: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
