package com.vibeactions.domain.model

import com.vibeactions.data.db.entities.MacroEntity
import com.vibeactions.data.db.entities.MacroLogEntity

fun MacroEntity.toDomain() = Macro(
    id = id, name = name, triggerType = TriggerType.valueOf(triggerType),
    scheduledTime = scheduledTime, repeatDaily = repeatDaily, recipientNumber = recipientNumber,
    messageBody = messageBody, enabled = enabled, lastTriggeredAt = lastTriggeredAt,
    lastStatus = lastStatus?.let { MacroStatus.valueOf(it) }, createdAt = createdAt,
    lastScheduledFireAt = lastScheduledFireAt, sortOrder = sortOrder
)

fun Macro.toEntity() = MacroEntity(
    id = id, name = name, triggerType = triggerType.name, scheduledTime = scheduledTime,
    repeatDaily = repeatDaily, recipientNumber = recipientNumber, messageBody = messageBody,
    enabled = enabled, lastTriggeredAt = lastTriggeredAt, lastStatus = lastStatus?.name,
    createdAt = createdAt, lastScheduledFireAt = lastScheduledFireAt, sortOrder = sortOrder
)

fun MacroLogEntity.toDomain() = MacroLog(
    id = id, macroId = macroId, triggeredAt = triggeredAt,
    status = MacroStatus.valueOf(status), messagePreview = messagePreview, errorMessage = errorMessage
)

fun MacroLog.toEntity() = MacroLogEntity(
    id = id, macroId = macroId, triggeredAt = triggeredAt, status = status.name,
    messagePreview = messagePreview, errorMessage = errorMessage
)
