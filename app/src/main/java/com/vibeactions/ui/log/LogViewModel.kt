package com.vibeactions.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A log entry joined with the macro that produced it ([macro] is null once that macro is
 *  deleted). [aiMacro]: the macro writes with AI, so the sent text may be AI-generated and can be
 *  reported. */
data class LogRow(
    val log: MacroLog,
    val macroName: String?,
    val aiMacro: Boolean = false,
    val macro: Macro? = null
)

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

    /** Every entry for the chosen macro, before the status filter — the chips count from this. */
    private val macroRows: Flow<List<LogRow>> =
        combine(repo.observeAll(), macroRepo.observeAll(), macroFilter) { list, macros, macroId ->
            val byId = macros.associateBy { it.id }
            list.asSequence()
                .filter { macroId == null || it.macroId == macroId }
                .map { log ->
                    val macro = byId[log.macroId]
                    LogRow(log, macro?.name, macro?.aiReplyEnabled == true, macro)
                }
                .toList()
        }

    val logs: StateFlow<List<LogRow>> =
        combine(macroRows, filter) { rows, status -> rows.filter { status == null || it.log.status == status } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** How many entries each status chip would show (null = all). */
    val counts: StateFlow<Map<MacroStatus?, Int>> =
        macroRows.map { rows ->
            val byStatus: Map<MacroStatus?, Int> = rows.groupingBy { it.log.status }.eachCount()
            byStatus + (null to rows.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setFilter(status: MacroStatus?) { filter.value = status }
    fun setMacroFilter(macroId: String?) { macroFilter.value = macroId }
    fun clear() = viewModelScope.launch { repo.clear() }
}
