package com.vibeactions.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.AiSendMode
import com.vibeactions.domain.model.GeofenceTransition
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.STATE_TRIGGERS
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.util.ContactResolver
import com.vibeactions.util.consumedFireStampForNewMacro
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
    /** Folder this macro lives in; null = top level ("root") of the list. */
    val folderId: String? = null,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val weekInterval: Int = 1,
    val startEpochDay: Long? = null,
    /** SCHEDULED: when true this macro fires once on [oneOffEpochDay] instead of recurring weekly. */
    val oneOff: Boolean = false,
    /** The single date (epoch day) a one-off scheduled macro fires on; null = today when first shown. */
    val oneOffEpochDay: Long? = null,
    /** SCHEDULED: whether the send time is spread by ±[randomSpreadMinutes]. Kept separate from the
     *  value so clearing the number field (→ 0) doesn't collapse the input and lock the user out. */
    val randomSpreadEnabled: Boolean = false,
    val randomSpreadMinutes: Int = 5,
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
    val aiReplyInstruction: String = "",
    /** State triggers (CHARGING/BLUETOOTH/WIFI): fire on connect (true) or disconnect (false). */
    val triggerOnConnect: Boolean = true,
    /** BLUETOOTH device address / WIFI SSID to match; blank = any. */
    val triggerTarget: String = "",
    /** Human label for [triggerTarget] (e.g. the Bluetooth device name). */
    val triggerTargetLabel: String = ""
) {
    val nameValid get() = name.isNotBlank()
    /** Non-blank numbers (blanks are ignored on save); at least one, and every non-blank one valid. */
    val cleanRecipients get() = recipients.map { it.trim() }.filter { it.isNotBlank() }
    val phoneValid get() = cleanRecipients.isNotEmpty() && cleanRecipients.all { isValidPhone(it) }
    val messageValid get() = message.isNotBlank()
    // A one-off has no weekday selection (it fires on a single date), so the weekday rule doesn't
    // apply — without this exemption the hidden weekday picker could block save with no visible error.
    val daysValid get() = triggerType != TriggerType.SCHEDULED || oneOff || daysOfWeek.isNotEmpty()
    /** When the spread is enabled it needs a value of at least 1 minute. */
    val randomSpreadValid get() = !randomSpreadEnabled || randomSpreadMinutes >= 1
    /** Reply macros (auto-reply / missed call) answer the other party, so no recipient list. */
    val recipientsRequired get() =
        triggerType != TriggerType.INCOMING && triggerType != TriggerType.MISSED_CALL
    /** Location macros need a chosen point. */
    val locationValid get() = triggerType != TriggerType.LOCATION || (latitude != null && longitude != null)
    val canSave get() = nameValid && messageValid && daysValid && locationValid &&
        randomSpreadValid && (!recipientsRequired || phoneValid)
}

/** Pure mapping from editor state to a saveable [Macro]; testable without Android. */
fun EditorState.toMacro(id: String): Macro {
    val scheduled = triggerType == TriggerType.SCHEDULED
    val incoming = triggerType == TriggerType.INCOMING
    // Reply macros (auto-reply / missed call) answer the other party: no recipient list, and the
    // sender filter applies. Keyword matching and AI only make sense for SMS (there is a message).
    val reply = incoming || triggerType == TriggerType.MISSED_CALL
    val location = triggerType == TriggerType.LOCATION
    // A one-off scheduled macro reuses the recurring machinery: it's a single-weekday send anchored
    // on, and expiring on, the chosen date, with repeatDaily=false so it never re-arms after firing.
    val oneOffDate = if (scheduled && oneOff) LocalDate.ofEpochDay(oneOffEpochDay ?: LocalDate.now().toEpochDay()) else null
    val interval = if (scheduled && !oneOff) weekInterval.coerceAtLeast(1) else 1
    // Anchor the multi-week rhythm on the first actual fire (first allowed weekday on/after the
    // chosen start date), so parity starts cleanly. Weekly macros need no anchor.
    val anchor = when {
        oneOffDate != null -> oneOffDate.toEpochDay()
        scheduled && interval > 1 -> {
            val start = startEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
            firstScheduledDateOnOrAfter(start, daysOfWeek).toEpochDay()
        }
        else -> null
    }
    return Macro(
        id = id,
        name = name.trim(),
        triggerType = triggerType,
        scheduledTime = if (scheduled) scheduledTime else null,
        repeatDaily = oneOffDate == null,
        // Auto-reply macros answer the incoming sender. A recipient list left over from a previous
        // trigger type must be dropped: the failed-send Retry action re-fires via macro.recipients
        // and would resend the fixed body to numbers that no longer apply.
        recipients = if (reply) emptyList() else cleanRecipients,
        messageBody = message,
        enabled = enabled,
        createdAt = createdAt,
        lastTriggeredAt = lastTriggeredAt,
        lastStatus = lastStatus,
        lastScheduledFireAt = lastScheduledFireAt,
        sortOrder = sortOrder,
        folderId = folderId,
        daysOfWeek = when {
            oneOffDate != null -> setOf(oneOffDate.dayOfWeek.value)
            scheduled -> daysOfWeek
            else -> setOf(1, 2, 3, 4, 5, 6, 7)
        },
        weekInterval = interval,
        anchorEpochDay = anchor,
        // Random send-time spread only applies to scheduled macros, and only when toggled on.
        randomSpreadMinutes = if (scheduled && randomSpreadEnabled) randomSpreadMinutes.coerceAtLeast(0) else 0,
        cardColor = cardColor,
        // AI fields apply to auto-replies (Gemini answers the incoming SMS) and to scheduled
        // macros (Gemini writes a fresh variation of the fixed message on every fire).
        aiReplyEnabled = if (incoming || scheduled) aiReplyEnabled else false,
        aiSendMode = aiSendMode,
        aiReplyInstruction = if ((incoming || scheduled) && aiReplyEnabled)
            aiReplyInstruction.trim().ifBlank { null } else null,
        triggerOnConnect = if (triggerType in STATE_TRIGGERS) triggerOnConnect else true,
        triggerTarget = if (triggerType == TriggerType.BLUETOOTH || triggerType == TriggerType.WIFI)
            triggerTarget.trim().ifBlank { null } else null,
        triggerTargetLabel = if (triggerType == TriggerType.BLUETOOTH || triggerType == TriggerType.WIFI)
            triggerTargetLabel.trim().ifBlank { null } else null,
        // A one-off expires on its own date so it can't fire on the same weekday in later weeks.
        validUntilEpochDay = when {
            oneOffDate != null -> oneOffDate.toEpochDay()
            scheduled -> validUntilEpochDay
            else -> null
        },
        matchSender = if (reply) matchSender.trim().ifBlank { null } else null,
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
    private val save: SaveMacroUseCase,
    private val contacts: ContactResolver
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    /** A short label shown under a recipient field: the contact name, "din egen telefon", both, or
     *  null when the number is blank / unknown / contacts permission is off. */
    suspend fun recipientLabel(number: String): String? {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return null
        val name = contacts.displayName(trimmed)
        val own = contacts.isOwnNumber(trimmed)
        return when {
            name != null && own -> "$name · din egen telefon"
            name != null -> name
            own -> "din egen telefon"
            else -> null
        }
    }

    /** What {modtager} resolves to for [number]: the contact name, else the number itself. */
    suspend fun recipientName(number: String): String {
        val trimmed = number.trim()
        return contacts.displayName(trimmed) ?: trimmed
    }

    /** True when any recipient is this device's own number — a common footgun (you text yourself). */
    fun anyRecipientIsOwnNumber(numbers: List<String>): Boolean =
        numbers.any { it.isNotBlank() && contacts.isOwnNumber(it) }

    fun load(macroId: String?) {
        if (macroId == null) { _state.value = EditorState(); return }
        viewModelScope.launch {
            repo.getById(macroId)?.let { m ->
                _state.value = EditorState(m.id, m.name, m.triggerType,
                    m.scheduledTime ?: "09:00", m.recipients.ifEmpty { listOf("") }, m.messageBody,
                    m.enabled, m.createdAt,
                    m.lastTriggeredAt, m.lastStatus, m.lastScheduledFireAt, m.sortOrder,
                    folderId = m.folderId,
                    daysOfWeek = m.daysOfWeek, weekInterval = m.weekInterval,
                    startEpochDay = m.anchorEpochDay,
                    // A scheduled macro with repeatDaily=false is a one-off; its date is the anchor
                    // (== the expiry). Recurring macros keep oneOff=false.
                    oneOff = m.triggerType == TriggerType.SCHEDULED && !m.repeatDaily,
                    oneOffEpochDay = if (m.triggerType == TriggerType.SCHEDULED && !m.repeatDaily)
                        (m.anchorEpochDay ?: m.validUntilEpochDay) else null,
                    randomSpreadEnabled = m.randomSpreadMinutes > 0,
                    randomSpreadMinutes = if (m.randomSpreadMinutes > 0) m.randomSpreadMinutes else 5,
                    validUntilEpochDay = m.validUntilEpochDay,
                    matchSender = m.matchSender ?: "", matchKeyword = m.matchKeyword ?: "",
                    latitude = m.latitude, longitude = m.longitude,
                    radiusMeters = m.radiusMeters ?: 200f,
                    geofenceTransition = m.geofenceTransition ?: GeofenceTransition.ENTER,
                    cardColor = if (m.cardColor != 0L) m.cardColor else _state.value.cardColor,
                    aiReplyEnabled = m.aiReplyEnabled,
                    aiSendMode = m.aiSendMode,
                    aiReplyInstruction = m.aiReplyInstruction ?: "",
                    triggerOnConnect = m.triggerOnConnect,
                    triggerTarget = m.triggerTarget ?: "",
                    triggerTargetLabel = m.triggerTargetLabel ?: "")
            }
        }
    }

    fun update(transform: (EditorState) -> EditorState) { _state.value = transform(_state.value) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        val isNew = s.id == null
        val id = s.id ?: UUID.randomUUID().toString()
        _state.value = _state.value.copy(id = id)
        viewModelScope.launch {
            var macro = s.toMacro(id)
            // A brand-new macro created after today's fire time missed nothing — consume today's
            // scheduled fire so the catch-up worker doesn't send it immediately on creation.
            if (isNew && macro.triggerType == TriggerType.SCHEDULED) {
                macro = macro.copy(
                    lastScheduledFireAt = consumedFireStampForNewMacro(
                        macro.scheduledTime, macro.daysOfWeek, macro.weekInterval,
                        macro.anchorEpochDay, macro.validUntilEpochDay
                    )
                )
            }
            save(macro); onDone()
        }
    }
}
