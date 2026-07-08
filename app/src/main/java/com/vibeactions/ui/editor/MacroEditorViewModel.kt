package com.vibeactions.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.domain.model.GeofenceTransition
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.util.firstScheduledDateOnOrAfter
import com.vibeactions.util.isValidPhone
import com.vibeactions.util.randomCardColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class EditorState(
    val id: String? = null,
    val name: String = "",
    val triggerType: TriggerType = TriggerType.SCHEDULED,
    val scheduledTime: String = "09:00",
    val recipients: List<String> = listOf(""),
    val message: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    val lastStatus: com.vibeactions.domain.model.MacroStatus? = null,
    val lastScheduledFireAt: Long? = null,
    val sortOrder: Int = 0,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val weekInterval: Int = 1,
    val startEpochDay: Long? = null,
    val validUntilEpochDay: Long? = null,
    val matchSender: String = "",
    val matchKeyword: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Float = 200f,
    val geofenceTransition: Int = GeofenceTransition.ENTER,
    val cardColor: Long = randomCardColor(),
    val aiReplyEnabled: Boolean = false,
    val aiSendMode: AiSendMode = AiSendMode.APPROVE,
    val aiReplyInstruction: String = ""
) {
    val nameValid get() = name.isNotBlank()
    /** Non-blank numbers (blanks are ignored on save); at least one, and every non-blank one valid. */
    val cleanRecipients get() = recipients.map { it.trim() }.filter { it.isNotBlank() }
    val phoneValid get() = cleanRecipients.isNotEmpty() && cleanRecipients.all { isValidPhone(it) }
    val messageValid get() = message.isNotBlank()
    val daysValid get() = triggerType != TriggerType.SCHEDULED || daysOfWeek.isNotEmpty()
    /** Auto-reply macros reply to the incoming sender, so they need no recipient list. */
    val recipientsRequired get() = triggerType != TriggerType.INCOMING
    /** Location macros need a chosen point. */
    val locationValid get() = triggerType != TriggerType.LOCATION || (latitude != null && longitude != null)
    val canSave get() = nameValid && messageValid && daysValid && locationValid &&
        (!recipientsRequired || phoneValid)
}

/** Pure mapping from editor state to a saveable [Macro]; testable without Android. */
fun EditorState.toMacro(id: String): Macro {
    val scheduled = triggerType == TriggerType.SCHEDULED
    val incoming = triggerType == TriggerType.INCOMING
    val location = triggerType == TriggerType.LOCATION
    val interval = if (scheduled) weekInterval.coerceAtLeast(1) else 1
    // Anchor the multi-week rhythm on the first actual fire (first allowed weekday on/after the
    // chosen start date), so parity starts cleanly. Weekly macros need no anchor.
    val anchor = if (scheduled && interval > 1) {
        val start = startEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
        firstScheduledDateOnOrAfter(start, daysOfWeek).toEpochDay()
    } else null
    return Macro(
        id = id,
        name = name.trim(),
        triggerType = triggerType,
        scheduledTime = if (scheduled) scheduledTime else null,
        repeatDaily = true,
        // Auto-reply macros answer the incoming sender. A recipient list left over from a previous
        // trigger type must be dropped: the failed-send Retry action re-fires via macro.recipients
        // and would resend the fixed body to numbers that no longer apply.
        recipients = if (incoming) emptyList() else cleanRecipients,
        messageBody = message,
        enabled = enabled,
        createdAt = createdAt,
        lastTriggeredAt = lastTriggeredAt,
        lastStatus = lastStatus,
        lastScheduledFireAt = lastScheduledFireAt,
        sortOrder = sortOrder,
        daysOfWeek = if (scheduled) daysOfWeek else setOf(1, 2, 3, 4, 5, 6, 7),
        weekInterval = interval,
        anchorEpochDay = anchor,
        cardColor = cardColor,
        aiReplyEnabled = if (incoming) aiReplyEnabled else false,
        aiSendMode = aiSendMode,
        aiReplyInstruction = if (incoming && aiReplyEnabled)
            aiReplyInstruction.trim().ifBlank { null } else null,
        validUntilEpochDay = if (scheduled) validUntilEpochDay else null,
        matchSender = if (incoming) matchSender.trim().ifBlank { null } else null,
        matchKeyword = if (incoming) matchKeyword.trim().ifBlank { null } else null,
        latitude = if (location) latitude else null,
        longitude = if (location) longitude else null,
        radiusMeters = if (location) radiusMeters else null,
        geofenceTransition = if (location) geofenceTransition else null
    )
}

@HiltViewModel
class MacroEditorViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val save: SaveMacroUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    fun load(macroId: String?) {
        if (macroId == null) { _state.value = EditorState(); return }
        viewModelScope.launch {
            repo.getById(macroId)?.let { m ->
                _state.value = EditorState(m.id, m.name, m.triggerType,
                    m.scheduledTime ?: "09:00", m.recipients.ifEmpty { listOf("") }, m.messageBody,
                    m.enabled, m.createdAt,
                    m.lastTriggeredAt, m.lastStatus, m.lastScheduledFireAt, m.sortOrder, m.daysOfWeek,
                    m.weekInterval, m.anchorEpochDay, validUntilEpochDay = m.validUntilEpochDay,
                    matchSender = m.matchSender ?: "", matchKeyword = m.matchKeyword ?: "",
                    latitude = m.latitude, longitude = m.longitude,
                    radiusMeters = m.radiusMeters ?: 200f,
                    geofenceTransition = m.geofenceTransition ?: GeofenceTransition.ENTER,
                    cardColor = if (m.cardColor != 0L) m.cardColor else _state.value.cardColor,
                    aiReplyEnabled = m.aiReplyEnabled,
                    aiSendMode = m.aiSendMode,
                    aiReplyInstruction = m.aiReplyInstruction ?: "")
            }
        }
    }

    fun update(transform: (EditorState) -> EditorState) { _state.value = transform(_state.value) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        val id = s.id ?: UUID.randomUUID().toString()
        _state.value = _state.value.copy(id = id)
        viewModelScope.launch { save(s.toMacro(id)); onDone() }
    }
}
