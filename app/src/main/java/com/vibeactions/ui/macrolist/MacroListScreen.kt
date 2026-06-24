package com.vibeactions.ui.macrolist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.Macro
import com.vibeactions.ui.common.MacroCard
import com.vibeactions.ui.editor.MacroEditorScreen
import com.vibeactions.ui.theme.OnSurfaceVariant
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroListScreen(
    banner: @Composable () -> Unit = {},
    vm: MacroListViewModel = hiltViewModel()
) {
    val macros by vm.macros.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var ordered by remember { mutableStateOf(macros) }
    LaunchedEffect(macros) { ordered = macros }

    var showEditor by remember { mutableStateOf(false) }
    var editorMacroId by remember { mutableStateOf<String?>(null) }

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    // Shared action handlers — used by both the card long-press menu and the editor sheet.
    fun deleteMacro(macro: Macro) {
        vm.onDelete(macro)
        scope.launch {
            val result = snackbar.showSnackbar("Macro deleted", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) vm.onUndoDelete(macro)
        }
    }
    fun sendMacro(macro: Macro) {
        vm.onTrigger(macro)
        scope.launch { snackbar.showSnackbar("Sending ${macro.name}…") }
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editorMacroId = null; showEditor = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "New macro") },
                text = { Text("New Macro") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 8.dp)) {
            banner()
            if (ordered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No macros yet. Tap 'New Macro' to create your first.",
                        color = OnSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(ordered, key = { it.id }) { macro ->
                        ReorderableItem(reorderState, key = macro.id) { _ ->
                            MacroCard(
                                macro = macro,
                                onClick = { editorMacroId = macro.id; showEditor = true },
                                onDelete = { deleteMacro(macro) },
                                onCopy = { vm.onCopy(macro) },
                                onSend = { sendMacro(macro) },
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStopped = { vm.onReorder(ordered.map(Macro::id)) }
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val editingMacro: Macro? = ordered.find { it.id == editorMacroId }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showEditor = false },
            sheetState = sheetState,
            containerColor = Color(0xFF1C1C1E)
        ) {
            MacroEditorScreen(
                macroId = editorMacroId,
                vm = hiltViewModel(key = editorMacroId ?: "new"),
                onDone = { showEditor = false },
                onDelete = editingMacro?.let { macro ->
                    { deleteMacro(macro); showEditor = false }
                },
                onCopy = editingMacro?.let { macro ->
                    { vm.onCopy(macro); showEditor = false }
                },
                onSend = editingMacro?.let { macro ->
                    { sendMacro(macro); showEditor = false }
                }
            )
        }
    }
}
