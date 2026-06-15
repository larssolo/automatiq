package com.vibeactions.ui.macrolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.usecase.DeleteMacroUseCase
import com.vibeactions.domain.usecase.ToggleMacroUseCase
import com.vibeactions.domain.usecase.TriggerMacroUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MacroListViewModel @Inject constructor(
    repo: MacroRepository,
    private val toggle: ToggleMacroUseCase,
    private val delete: DeleteMacroUseCase,
    private val trigger: TriggerMacroUseCase
) : ViewModel() {
    val macros: StateFlow<List<Macro>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onToggle(macro: Macro, enabled: Boolean) = viewModelScope.launch { toggle(macro, enabled) }
    fun onDelete(macro: Macro) = viewModelScope.launch { delete(macro) }
    fun onTrigger(macro: Macro) = viewModelScope.launch { trigger(macro.id) }
}
