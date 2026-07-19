package com.vibeactions.util

import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class MacroDto(
    val id: String, val name: String, val triggerType: String, val scheduledTime: String?,
    val repeatDaily: Boolean, val recipientNumber: String? = null,
    val recipients: List<String> = emptyList(), val messageBody: String,
    val enabled: Boolean, val lastTriggeredAt: Long?, val lastStatus: String?, val createdAt: Long,
    val lastScheduledFireAt: Long? = null, val sortOrder: Int = 0,
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val weekInterval: Int = 1, val anchorEpochDay: Long? = null,
    val cardColor: Long = 0L, val validUntilEpochDay: Long? = null,
    val matchSender: String? = null, val matchKeyword: String? = null,
    val latitude: Double? = null, val longitude: Double? = null,
    val radiusMeters: Float? = null, val geofenceTransition: Int? = null,
    val aiReplyEnabled: Boolean = false,
    val aiSendMode: String = "APPROVE",
    val aiReplyInstruction: String? = null,
    val triggerOnConnect: Boolean = true,
    val triggerTarget: String? = null,
    val triggerTargetLabel: String? = null,
    val folderId: String? = null
)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

internal fun Macro.toDto() = MacroDto(
    id = id, name = name, triggerType = triggerType.name,
    scheduledTime = scheduledTime, repeatDaily = repeatDaily,
    recipients = recipients, messageBody = messageBody, enabled = enabled,
    lastTriggeredAt = lastTriggeredAt, lastStatus = lastStatus?.name,
    createdAt = createdAt, lastScheduledFireAt = lastScheduledFireAt,
    sortOrder = sortOrder, daysOfWeek = daysOfWeek.sorted(),
    weekInterval = weekInterval, anchorEpochDay = anchorEpochDay,
    cardColor = cardColor, validUntilEpochDay = validUntilEpochDay,
    matchSender = matchSender, matchKeyword = matchKeyword,
    latitude = latitude, longitude = longitude,
    radiusMeters = radiusMeters, geofenceTransition = geofenceTransition,
    aiReplyEnabled = aiReplyEnabled,
    aiSendMode = aiSendMode.name,
    aiReplyInstruction = aiReplyInstruction,
    triggerOnConnect = triggerOnConnect,
    triggerTarget = triggerTarget,
    triggerTargetLabel = triggerTargetLabel,
    folderId = folderId
)

internal fun MacroDto.toMacro(): Macro {
    // Reject a malformed time up front (hand-edited file): once persisted it would crash scheduling
    // on every app start. Throwing here surfaces as a clean import error instead.
    require(scheduledTime == null || parseHhMmOrNull(scheduledTime) != null) {
        "invalid scheduledTime \"$scheduledTime\" for macro \"$name\""
    }
    return Macro(
        id = id, name = name, triggerType = TriggerType.valueOf(triggerType),
        scheduledTime = scheduledTime, repeatDaily = repeatDaily,
        // New exports carry `recipients`; legacy single-number exports fall back to recipientNumber.
        recipients = recipients.ifEmpty { listOfNotNull(recipientNumber) },
        messageBody = messageBody, enabled = enabled, lastTriggeredAt = lastTriggeredAt,
        lastStatus = lastStatus?.let { s -> MacroStatus.valueOf(s) }, createdAt = createdAt,
        lastScheduledFireAt = lastScheduledFireAt, sortOrder = sortOrder,
        daysOfWeek = daysOfWeek.toSet(), weekInterval = weekInterval,
        anchorEpochDay = anchorEpochDay, cardColor = cardColor,
        validUntilEpochDay = validUntilEpochDay,
        matchSender = matchSender, matchKeyword = matchKeyword,
        latitude = latitude, longitude = longitude,
        radiusMeters = radiusMeters, geofenceTransition = geofenceTransition,
        aiReplyEnabled = aiReplyEnabled,
        aiSendMode = AiSendMode.valueOf(aiSendMode),
        aiReplyInstruction = aiReplyInstruction,
        triggerOnConnect = triggerOnConnect,
        triggerTarget = triggerTarget,
        triggerTargetLabel = triggerTargetLabel,
        folderId = folderId
    )
}

fun exportMacros(macros: List<Macro>): String =
    json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(MacroDto.serializer()),
        macros.map { it.toDto() }
    )

fun importMacros(text: String): List<Macro> =
    json.decodeFromString(
        kotlinx.serialization.builtins.ListSerializer(MacroDto.serializer()), text
    ).map { it.toMacro() }
