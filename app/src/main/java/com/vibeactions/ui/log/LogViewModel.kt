package com.vibeactions.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A log entry joined with the (current) name of the macro that produced it. */
data class LogRow(val log: MacroLog, val macroName: String?)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repo: MacroLogRepository,
    macroRepo: MacroRepository
) : ViewModel() {
    private val filter = MutableStateFlow<MacroStatus?>(null)
    val statusFilter: StateFlow<MacroStatus?> = filter

    private val macroFilter = MutableStateFlow<String?>(null)
    val selectedMacroId: StateFlow<String?> = macroFilter

    /** (id, name) of every macro, for the per-macro filter dropdown. */
    val macroOptions: StateFlow<List<Pair<String, String>>> =
        macroRepo.observeAll()
            .map { macros -> macros.map { it.id to it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<LogRow>> =
        combine(repo.observeAll(), macroRepo.observeAll(), filter, macroFilter) { list, macros, status, macroId ->
            val names = macros.associate { it.id to it.name }
            list.asSequence()
                .filter { status == null || it.status == status }
                .filter { macroId == null || it.macroId == macroId }
                .map { LogRow(it, names[it.macroId]) }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(status: MacroStatus?) { filter.value = status }
    fun setMacroFilter(macroId: String?) { macroFilter.value = macroId }
    fun clear() = viewModelScope.launch { repo.clear() }
}
