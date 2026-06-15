package com.vibeactions.domain.model

data class MacroLog(
    val id: Long = 0,
    val macroId: String,
    val triggeredAt: Long,
    val status: MacroStatus,
    val messagePreview: String?,
    val errorMessage: String? = null
)
