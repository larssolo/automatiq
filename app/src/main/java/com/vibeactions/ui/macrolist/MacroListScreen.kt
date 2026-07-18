package com.vibeactions.ui.macrolist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
    var query by remember { mutableStateOf("") }
    // Filter for display; reordering is disabled while searching (dragging a filtered subset would
    // scramble the persisted order), so the drag handle only appears on the full, unfiltered list.
    val searching = query.isNotBlank()
    val visible = if (searching) ordered.filter { it.name.contains(query.trim(), ignoreCase = true) }
        else ordered

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
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editorMacroId = null; showEditor = true },
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "New macro")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 8.dp)) {
            banner()
            if (ordered.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search macros") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            if (ordered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No macros yet. Tap + to create your first.",
                        color = OnSurfaceVariant)
                }
            } else if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No macros match \"${query.trim()}\".", color = OnSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                ) {
                    items(visible, key = { it.id }) { macro ->
                        ReorderableItem(reorderState, key = macro.id) { _ ->
                            MacroCard(
                                macro = macro,
                                onClick = { editorMacroId = macro.id; showEditor = true },
                                onDelete = { deleteMacro(macro) },
                                onCopy = { vm.onCopy(macro) },
                                onSend = { sendMacro(macro) },
                                onToggle = { enabled -> vm.onToggle(macro, enabled) },
                                // No drag while searching — reordering a filtered subset would
                                // corrupt the saved order.
                                dragHandleModifier = if (searching) Modifier else Modifier.draggableHandle(
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
