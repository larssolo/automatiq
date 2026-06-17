package com.vibeactions.domain.model

import com.vibeactions.data.db.entities.MacroEntity
import com.vibeactions.data.db.entities.MacroLogEntity

/** Bitmask (bit day-1 set) -> set of ISO day numbers 1..7. */
fun Int.toDaySet(): Set<Int> = (1..7).filter { (this shr (it - 1)) and 1 == 1 }.toSet()

/** Set of ISO day numbers -> bitmask. */
fun Set<Int>.toDayMask(): Int = fold(0) { acc, d -> acc or (1 shl (d - 1)) }

/** Comma-joined recipient column -> list (trims blanks). Single legacy numbers map to a 1-element list. */
fun String.toRecipientList(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun MacroEntity.toDomain() = Macro(
    id = id, name = name, triggerType = TriggerType.valueOf(triggerType),
    scheduledTime = scheduledTime, repeatDaily = repeatDaily, recipients = recipientNumber.toRecipientList(),
    messageBody = messageBody, enabled = enabled, lastTriggeredAt = lastTriggeredAt,
    lastStatus = lastStatus?.let { MacroStatus.valueOf(it) }, createdAt = createdAt,
    lastScheduledFireAt = lastScheduledFireAt, sortOrder = sortOrder,
    daysOfWeek = daysOfWeek.toDaySet(), weekInterval = weekInterval, anchorEpochDay = anchorEpochDay,
    cardColor = cardColor, validUntilEpochDay = validUntilEpochDay,
    matchSender = matchSender, matchKeyword = matchKeyword
)

fun Macro.toEntity() = MacroEntity(
    id = id, name = name, triggerType = triggerType.name, scheduledTime = scheduledTime,
    repeatDaily = repeatDaily, recipientNumber = recipients.joinToString(","), messageBody = messageBody,
    enabled = enabled, lastTriggeredAt = lastTriggeredAt, lastStatus = lastStatus?.name,
    createdAt = createdAt, lastScheduledFireAt = lastScheduledFireAt, sortOrder = sortOrder,
    daysOfWeek = daysOfWeek.toDayMask(), weekInterval = weekInterval, anchorEpochDay = anchorEpochDay,
    cardColor = cardColor, validUntilEpochDay = validUntilEpochDay,
    matchSender = matchSender, matchKeyword = matchKeyword
)

fun MacroLogEntity.toDomain() = MacroLog(
    id = id, macroId = macroId, triggeredAt = triggeredAt,
    status = MacroStatus.valueOf(status), messagePreview = messagePreview, errorMessage = errorMessage
)

fun MacroLog.toEntity() = MacroLogEntity(
    id = id, macroId = macroId, triggeredAt = triggeredAt, status = status.name,
    messagePreview = messagePreview, errorMessage = errorMessage
)
