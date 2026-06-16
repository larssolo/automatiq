package com.vibeactions.ui.macrolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.usecase.DeleteMacroUseCase
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.domain.usecase.ToggleMacroUseCase
import com.vibeactions.domain.usecase.TriggerMacroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MacroListViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val toggle: ToggleMacroUseCase,
    private val delete: DeleteMacroUseCase,
    private val save: SaveMacroUseCase,
    private val trigger: TriggerMacroUseCase
) : ViewModel() {
    val macros: StateFlow<List<Macro>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onToggle(macro: Macro, enabled: Boolean) = viewModelScope.launch { toggle(macro, enabled) }
    fun onDelete(macro: Macro) = viewModelScope.launch { delete(macro) }
    fun onUndoDelete(macro: Macro) = viewModelScope.launch { save(macro) }
    fun onTrigger(macro: Macro) = viewModelScope.launch { trigger(macro.id) }

    /** Duplicate a macro: new id, "(copy)" name, cleared run history; same trigger/recipient/body.
     *  Goes through SaveMacroUseCase so a scheduled+enabled copy is armed like any other. */
    fun onCopy(macro: Macro) = viewModelScope.launch {
        save(
            macro.copy(
                id = UUID.randomUUID().toString(),
                name = macro.name + " (copy)",
                lastTriggeredAt = null,
                lastStatus = null,
                lastScheduledFireAt = null,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /** Persist a drag-and-drop reorder: ids in their new top-to-bottom order. */
    fun onReorder(orderedIds: List<String>) = viewModelScope.launch { repo.persistOrder(orderedIds) }
}
