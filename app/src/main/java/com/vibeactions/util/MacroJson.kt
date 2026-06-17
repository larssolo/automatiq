package com.vibeactions.util

import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class MacroDto(
    val id: String, val name: String, val triggerType: String, val scheduledTime: String?,
    val repeatDaily: Boolean, val recipientNumber: String, val messageBody: String,
    val enabled: Boolean, val lastTriggeredAt: Long?, val lastStatus: String?, val createdAt: Long,
    val lastScheduledFireAt: Long? = null, val sortOrder: Int = 0,
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val weekInterval: Int = 1, val anchorEpochDay: Long? = null,
    val cardColor: Long = 0L
)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

fun exportMacros(macros: List<Macro>): String =
    json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(MacroDto.serializer()),
        macros.map {
            MacroDto(it.id, it.name, it.triggerType.name, it.scheduledTime, it.repeatDaily,
                it.recipientNumber, it.messageBody, it.enabled, it.lastTriggeredAt,
                it.lastStatus?.name, it.createdAt, it.lastScheduledFireAt, it.sortOrder,
                it.daysOfWeek.sorted(), it.weekInterval, it.anchorEpochDay, it.cardColor)
        }
    )

fun importMacros(text: String): List<Macro> =
    json.decodeFromString(
        kotlinx.serialization.builtins.ListSerializer(MacroDto.serializer()), text
    ).map {
        Macro(it.id, it.name, TriggerType.valueOf(it.triggerType), it.scheduledTime, it.repeatDaily,
            it.recipientNumber, it.messageBody, it.enabled, it.lastTriggeredAt,
            it.lastStatus?.let { s -> MacroStatus.valueOf(s) }, it.createdAt,
            it.lastScheduledFireAt, it.sortOrder, it.daysOfWeek.toSet(),
            it.weekInterval, it.anchorEpochDay, it.cardColor)
    }
