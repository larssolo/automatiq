package com.vibeactions.ui.macrolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibeactions.data.repository.FolderRepository
import com.vibeactions.data.repository.MacroRepository
import com.vibeactions.domain.model.Folder
import com.vibeactions.domain.model.Macro
import com.vibeactions.domain.usecase.DeleteMacroUseCase
import com.vibeactions.domain.usecase.SaveMacroUseCase
import com.vibeactions.domain.usecase.ToggleFolderUseCase
import com.vibeactions.domain.usecase.ToggleMacroUseCase
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.domain.usecase.TriggerMacroUseCase
import com.vibeactions.util.ListRow
import com.vibeactions.util.consumedFireStampForNewMacro
import com.vibeactions.util.flattenRows
import com.vibeactions.util.layoutOrders
import com.vibeactions.util.randomCardColor
import com.vibeactions.util.resolveDrop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MacroListViewModel @Inject constructor(
    private val repo: MacroRepository,
    private val folderRepo: FolderRepository,
    private val toggle: ToggleMacroUseCase,
    private val toggleFolder: ToggleFolderUseCase,
    private val delete: DeleteMacroUseCase,
    private val save: SaveMacroUseCase,
    private val trigger: TriggerMacroUseCase
) : ViewModel() {
    val macros: StateFlow<List<Macro>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> =
        folderRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The folder-aware flat row list the screen renders (folders + members + root macros). */
    val rows: StateFlow<List<ListRow>> =
        combine(folderRepo.observeAll(), repo.observeAll()) { folders, macros ->
            flattenRows(folders, macros)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                // Like a new macro: if today's fire time already passed, consume it so the
                // catch-up worker doesn't send the fresh copy immediately.
                lastScheduledFireAt = if (macro.triggerType == TriggerType.SCHEDULED)
                    consumedFireStampForNewMacro(
                        macro.scheduledTime, macro.daysOfWeek, macro.weekInterval,
                        macro.anchorEpochDay, macro.validUntilEpochDay
                    ) else null,
                createdAt = System.currentTimeMillis(),
                cardColor = randomCardColor()
            )
        )
    }

    /** Persists a finished drag: the dragged macro's (possibly new) folder plus every visible
     *  row's order. Membership was decided by resolveDrop from the landing position. */
    fun onDrop(rows: List<ListRow>, draggedKey: String) = viewModelScope.launch {
        val result = resolveDrop(rows, draggedKey)
        if (draggedKey.startsWith("m:")) {
            repo.setFolder(draggedKey.removePrefix("m:"), result.movedMacroFolderId)
        }
        val orders = layoutOrders(result.rows)
        orders.folderOrder.forEach { (id, order) -> folderRepo.updateSortOrder(id, order) }
        orders.rootMacroOrder.forEach { (id, order) -> repo.updateSortOrder(id, order) }
        orders.memberOrder.forEach { (_, members) ->
            members.forEach { (id, order) -> repo.updateSortOrder(id, order) }
        }
    }

    fun onToggleExpanded(folder: Folder) =
        viewModelScope.launch { folderRepo.setExpanded(folder.id, !folder.expanded) }

    /** New folders land at the very top of the list, expanded and ready to drag into. */
    fun onCreateFolder(name: String) = viewModelScope.launch {
        val minRoot = minOf(
            folders.value.minOfOrNull { it.sortOrder } ?: 0,
            macros.value.filter { it.folderId == null }.minOfOrNull { it.sortOrder } ?: 0
        )
        folderRepo.upsert(
            Folder(id = UUID.randomUUID().toString(), name = name.trim(),
                cardColor = randomCardColor(), sortOrder = minRoot - 1)
        )
    }

    fun onRenameFolder(folder: Folder, name: String) =
        viewModelScope.launch { folderRepo.upsert(folder.copy(name = name.trim())) }

    /** Deletes the folder, moving members to root. Returns their ids so the screen's Undo
     *  snackbar can restore the memberships. */
    suspend fun deleteFolder(folder: Folder): List<String> {
        val members = repo.getByFolder(folder.id)
        val memberIds = members.map { it.id }
        // Freed members carry small in-folder sort orders (0,1,2…); left as-is they'd sort ABOVE
        // existing root items. Renumber them onto the tail of the root sort space so they stay put.
        val maxRoot = maxOf(
            folders.value.maxOfOrNull { it.sortOrder } ?: 0,
            macros.value.filter { it.folderId == null }.maxOfOrNull { it.sortOrder } ?: 0
        )
        repo.clearFolder(folder.id)
        members.forEachIndexed { i, m -> repo.updateSortOrder(m.id, maxRoot + 1 + i) }
        folderRepo.delete(folder)
        return memberIds
    }

    fun onUndoDeleteFolder(folder: Folder, memberIds: List<String>) = viewModelScope.launch {
        folderRepo.upsert(folder)
        memberIds.forEach { repo.setFolder(it, folder.id) }
    }

    fun onToggleFolder(folder: Folder, enabled: Boolean) =
        viewModelScope.launch { toggleFolder(folder.id, enabled) }

    /** Menu-driven move ("Move to folder…" / "Move to root"); null = root. Assigns a fresh
     *  sort_order at the tail of the destination so the macro lands at the bottom there rather than
     *  at a position dictated by its stale order in the space it came from. */
    fun onMoveToFolder(macro: Macro, folderId: String?) = viewModelScope.launch {
        repo.setFolder(macro.id, folderId)
        val tail = if (folderId == null) {
            maxOf(
                folders.value.maxOfOrNull { it.sortOrder } ?: -1,
                macros.value.filter { it.folderId == null && it.id != macro.id }
                    .maxOfOrNull { it.sortOrder } ?: -1
            )
        } else {
            macros.value.filter { it.folderId == folderId && it.id != macro.id }
                .maxOfOrNull { it.sortOrder } ?: -1
        }
        repo.updateSortOrder(macro.id, tail + 1)
    }
}
