package com.vibeactions.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.util.isValidPhone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EditorState(
    val id: String? = null,
    val name: String = "",
    val triggerType: TriggerType = TriggerType.SCHEDULED,
    val scheduledTime: String = "09:00",
    val recipient: String = "",
    val message: String = "",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    val lastStatus: com.vibeactions.domain.model.MacroStatus? = null,
    val lastScheduledFireAt: Long? = null,
    val sortOrder: Int = 0
) {
    val nameValid get() = name.isNotBlank()
    val phoneValid get() = isValidPhone(recipient)
    val messageValid get() = message.isNotBlank()
    val canSave get() = nameValid && phoneValid && messageValid
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
                    m.scheduledTime ?: "09:00", m.recipientNumber, m.messageBody, m.enabled, m.createdAt,
                    m.lastTriggeredAt, m.lastStatus, m.lastScheduledFireAt, m.sortOrder)
            }
        }
    }

    fun update(transform: (EditorState) -> EditorState) { _state.value = transform(_state.value) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        val id = s.id ?: UUID.randomUUID().toString()
        _state.value = _state.value.copy(id = id)
        val macro = Macro(
            id = id,
            name = s.name.trim(),
            triggerType = s.triggerType,
            scheduledTime = if (s.triggerType == TriggerType.SCHEDULED) s.scheduledTime else null,
            repeatDaily = true,
            recipientNumber = s.recipient.trim(),
            messageBody = s.message,
            enabled = s.enabled,
            createdAt = s.createdAt,
            lastTriggeredAt = s.lastTriggeredAt,
            lastStatus = s.lastStatus,
            lastScheduledFireAt = s.lastScheduledFireAt,
            sortOrder = s.sortOrder
        )
        viewModelScope.launch { save(macro); onDone() }
    }
}
