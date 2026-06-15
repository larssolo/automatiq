package com.vibeactions.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.util.exportMacros
import com.vibeactions.util.importMacros
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val save: SaveMacroUseCase
) : ViewModel() {

    fun export(onReady: (String) -> Unit) = viewModelScope.launch {
        onReady(exportMacros(repo.observeAll().first()))
    }

    fun import(json: String, onDone: (Int) -> Unit) = viewModelScope.launch {
        val macros = runCatching { importMacros(json) }.getOrDefault(emptyList())
        macros.forEach { save(it) }
        onDone(macros.size)
    }
}
