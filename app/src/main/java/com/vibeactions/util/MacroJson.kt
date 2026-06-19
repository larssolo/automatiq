package com.vibeactions.util

import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroStatus
import com.vibeactions.domain.model.TriggerType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class MacroDto(
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
    val aiSendMode: String = "APPROVE"
)

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

fun exportMacros(macros: List<Macro>): String =
    json.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(MacroDto.serializer()),
        macros.map {
            MacroDto(
                id = it.id, name = it.name, triggerType = it.triggerType.name,
                scheduledTime = it.scheduledTime, repeatDaily = it.repeatDaily,
                recipients = it.recipients, messageBody = it.messageBody, enabled = it.enabled,
                lastTriggeredAt = it.lastTriggeredAt, lastStatus = it.lastStatus?.name,
                createdAt = it.createdAt, lastScheduledFireAt = it.lastScheduledFireAt,
                sortOrder = it.sortOrder, daysOfWeek = it.daysOfWeek.sorted(),
                weekInterval = it.weekInterval, anchorEpochDay = it.anchorEpochDay,
                cardColor = it.cardColor, validUntilEpochDay = it.validUntilEpochDay,
                matchSender = it.matchSender, matchKeyword = it.matchKeyword,
                latitude = it.latitude, longitude = it.longitude,
                radiusMeters = it.radiusMeters, geofenceTransition = it.geofenceTransition,
                aiReplyEnabled = it.aiReplyEnabled,
                aiSendMode = it.aiSendMode.name)
        }
    )

fun importMacros(text: String): List<Macro> =
    json.decodeFromString(
        kotlinx.serialization.builtins.ListSerializer(MacroDto.serializer()), text
    ).map {
        Macro(
            id = it.id, name = it.name, triggerType = TriggerType.valueOf(it.triggerType),
            scheduledTime = it.scheduledTime, repeatDaily = it.repeatDaily,
            // New exports carry `recipients`; legacy single-number exports fall back to recipientNumber.
            recipients = it.recipients.ifEmpty { listOfNotNull(it.recipientNumber) },
            messageBody = it.messageBody, enabled = it.enabled, lastTriggeredAt = it.lastTriggeredAt,
            lastStatus = it.lastStatus?.let { s -> MacroStatus.valueOf(s) }, createdAt = it.createdAt,
            lastScheduledFireAt = it.lastScheduledFireAt, sortOrder = it.sortOrder,
            daysOfWeek = it.daysOfWeek.toSet(), weekInterval = it.weekInterval,
            anchorEpochDay = it.anchorEpochDay, cardColor = it.cardColor,
            validUntilEpochDay = it.validUntilEpochDay,
            matchSender = it.matchSender, matchKeyword = it.matchKeyword,
            latitude = it.latitude, longitude = it.longitude,
            radiusMeters = it.radiusMeters, geofenceTransition = it.geofenceTransition,
            aiReplyEnabled = it.aiReplyEnabled,
            aiSendMode = AiSendMode.valueOf(it.aiSendMode))
    }
