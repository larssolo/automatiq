package com.vibeactions.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.MacroLogRepository
import com.vibeactions.domain.model.MacroLog
import com.vibeactions.domain.model.MacroStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repo: MacroLogRepository
) : ViewModel() {
    private val filter = MutableStateFlow<MacroStatus?>(null)
    val statusFilter: StateFlow<MacroStatus?> = filter

    val logs: StateFlow<List<MacroLog>> =
        combine(repo.observeAll(), filter) { list, f ->
            if (f == null) list else list.filter { it.status == f }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(status: MacroStatus?) { filter.value = status }
    fun clear() = viewModelScope.launch { repo.clear() }
}
