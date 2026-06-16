package com.vibeactions.ui.macrolist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.Macro
import com.vibeactions.ui.common.MacroCard
import com.vibeactions.ui.theme.OnSurfaceVariant
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroListScreen(
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    banner: @Composable () -> Unit = {},
    vm: MacroListViewModel = hiltViewModel()
) {
    val macros by vm.macros.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Local copy that the user drags; reordered live and persisted on drop. Re-synced whenever the
    // database emits a new list (create/delete/toggle, and after a reorder is persisted).
    var ordered by remember { mutableStateOf(macros) }
    LaunchedEffect(macros) { ordered = macros }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Default.Add, contentDescription = "New macro") },
                text = { Text("New Macro") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            banner()
            if (ordered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No macros yet.\nTap “New Macro” to create your first.",
                        color = OnSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(ordered, key = { it.id }) { macro ->
                        ReorderableItem(reorderState, key = macro.id) { _ ->
                            MacroCard(
                                macro = macro,
                                dragHandle = {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .draggableHandle(
                                                onDragStopped = { vm.onReorder(ordered.map(Macro::id)) }
                                            )
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.DragHandle,
                                            contentDescription = "Reorder macro",
                                            tint = OnSurfaceVariant
                                        )
                                    }
                                },
                                onToggle = { vm.onToggle(macro, it) },
                                onTap = { vm.onTrigger(macro) },
                                onEdit = { onEdit(macro.id) },
                                onCopy = { vm.onCopy(macro) },
                                onDelete = {
                                    vm.onDelete(macro)
                                    scope.launch {
                                        val result = snackbar.showSnackbar(
                                            message = "Macro deleted",
                                            actionLabel = "Undo"
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            vm.onUndoDelete(macro)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
