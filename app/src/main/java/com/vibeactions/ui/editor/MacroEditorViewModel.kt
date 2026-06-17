package com.vibeactions.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
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
    val cardColor: Long = randomCardColor()
) {
    val nameValid get() = name.isNotBlank()
    /** Non-blank numbers (blanks are ignored on save); at least one, and every non-blank one valid. */
    val cleanRecipients get() = recipients.map { it.trim() }.filter { it.isNotBlank() }
    val phoneValid get() = cleanRecipients.isNotEmpty() && cleanRecipients.all { isValidPhone(it) }
    val messageValid get() = message.isNotBlank()
    val daysValid get() = triggerType != TriggerType.SCHEDULED || daysOfWeek.isNotEmpty()
    val canSave get() = nameValid && phoneValid && messageValid && daysValid
}

@HiltViewModel
class MacroEditorViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val save: SaveMacroUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    fun load(macroId: String?) {
        if (macroId == null) return
        viewModelScope.launch {
            repo.getById(macroId)?.let { m ->
                _state.value = EditorState(m.id, m.name, m.triggerType,
                    m.scheduledTime ?: "09:00", m.recipients.ifEmpty { listOf("") }, m.messageBody,
                    m.enabled, m.createdAt,
                    m.lastTriggeredAt, m.lastStatus, m.lastScheduledFireAt, m.sortOrder, m.daysOfWeek,
                    m.weekInterval, m.anchorEpochDay,
                    cardColor = if (m.cardColor != 0L) m.cardColor else _state.value.cardColor)
            }
        }
    }

    fun update(transform: (EditorState) -> EditorState) { _state.value = transform(_state.value) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        val id = s.id ?: UUID.randomUUID().toString()
        _state.value = _state.value.copy(id = id)
        val scheduled = s.triggerType == TriggerType.SCHEDULED
        val interval = if (scheduled) s.weekInterval.coerceAtLeast(1) else 1
        // Anchor the multi-week rhythm on the first actual fire (first allowed weekday on/after the
        // chosen start date), so parity starts cleanly. Weekly macros need no anchor.
        val anchor = if (scheduled && interval > 1) {
            val start = s.startEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
            firstScheduledDateOnOrAfter(start, s.daysOfWeek).toEpochDay()
        } else null
        val macro = Macro(
            id = id,
            name = s.name.trim(),
            triggerType = s.triggerType,
            scheduledTime = if (s.triggerType == TriggerType.SCHEDULED) s.scheduledTime else null,
            repeatDaily = true,
            recipients = s.cleanRecipients,
            messageBody = s.message,
            enabled = s.enabled,
            createdAt = s.createdAt,
            lastTriggeredAt = s.lastTriggeredAt,
            lastStatus = s.lastStatus,
            lastScheduledFireAt = s.lastScheduledFireAt,
            sortOrder = s.sortOrder,
            daysOfWeek = if (scheduled) s.daysOfWeek else setOf(1, 2, 3, 4, 5, 6, 7),
            weekInterval = interval,
            anchorEpochDay = anchor,
            cardColor = s.cardColor
        )
        viewModelScope.launch { save(macro); onDone() }
    }
}
