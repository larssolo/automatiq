package com.vibeactions.ui.macrolist

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.Folder
import com.vibeactions.domain.model.Macro
import com.vibeactions.ui.common.FolderCard
import com.vibeactions.ui.common.MacroCard
import com.vibeactions.ui.editor.MacroEditorScreen
import com.vibeactions.ui.theme.JetBrainsMono
import com.vibeactions.ui.theme.OnPrimary
import com.vibeactions.ui.theme.OnSurface
import com.vibeactions.ui.theme.OnSurfaceVariant
import com.vibeactions.ui.theme.Primary
import com.vibeactions.util.FolderRow
import com.vibeactions.util.ListRow
import com.vibeactions.util.MacroRow
import com.vibeactions.util.folderSwitchOn
import com.vibeactions.util.hideMembers
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
    val vmRows by vm.rows.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf(vmRows) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var hiddenMembers by remember { mutableStateOf<List<ListRow>>(emptyList()) }
    // Don't clobber the local list mid-drag: a background macro update (a scheduled fire, a delivery
    // report, an auto-reply status change) makes vm.rows re-emit, which would restore the folder's
    // hidden members while a drag holds them out — then dropped() re-splices them, duplicating
    // LazyGrid keys and crashing. Only sync when no drag is in progress.
    LaunchedEffect(vmRows) { if (draggedKey == null) rows = vmRows }
    var showCreateFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Folder?>(null) }
    var deleteTarget by remember { mutableStateOf<Folder?>(null) }
    var moveTarget by remember { mutableStateOf<Macro?>(null) }
    var fabMenu by remember { mutableStateOf(false) }

    var showEditor by remember { mutableStateOf(false) }
    var editorMacroId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    // Filter for display; reordering is disabled while searching (dragging a filtered subset would
    // scramble the persisted order), so the drag handle only appears on the full, unfiltered list.
    val searching = query.isNotBlank()
    val visible = if (searching) macros.filter { it.name.contains(query.trim(), ignoreCase = true) }
        else macros

    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        var current = rows
        val key = current.getOrNull(from.index)?.key
        if (key != null && key.startsWith("f:") && draggedKey != key) {
            draggedKey = key
            val folderId = key.removePrefix("f:")
            hiddenMembers = current.filter { it is MacroRow && it.macro.folderId == folderId }
            current = hideMembers(current, folderId)
        } else if (key != null && draggedKey == null) draggedKey = key
        val fromIdx = current.indexOfFirst { it.key == key }
        // Bail on an unknown key rather than coercing to 0, which would silently reorder an
        // unrelated item.
        if (fromIdx < 0) return@rememberReorderableLazyGridState
        rows = current.toMutableList().apply {
            add(to.index.coerceIn(0, size - 1), removeAt(fromIdx))
        }
    }

    // Shared action handlers — used by both the card long-press menu and the editor sheet.
    fun deleteMacro(macro: Macro) {
        vm.onDelete(macro)
        scope.launch {
            val result = snackbar.showSnackbar("\"${macro.name}\" deleted", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) vm.onUndoDelete(macro)
        }
    }
    fun sendMacro(macro: Macro) {
        vm.onTrigger(macro)
        scope.launch { snackbar.showSnackbar("On its way — ${macro.name}") }
    }
    fun dropped() {
        val key = draggedKey ?: return
        draggedKey = null
        vm.onDrop(rows, key)
        // vm.rows never re-emits a content-equal list (StateFlow conflation), so a folder drag
        // that lands back where it started would otherwise leave its members hidden forever.
        // Restore them locally; a real change is overwritten by the next vm emission anyway.
        // Only re-add members not already present, so this can never duplicate a LazyGrid key.
        if (hiddenMembers.isNotEmpty() && key.startsWith("f:")) {
            val idx = rows.indexOfFirst { it.key == key }
            val absent = hiddenMembers.filter { m -> rows.none { it.key == m.key } }
            if (idx >= 0 && absent.isNotEmpty()) {
                rows = rows.toMutableList().apply { addAll(idx + 1, absent) }
            }
        }
        hiddenMembers = emptyList()
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { fabMenu = true },
                    // Blob cut — the leaf-corner signature, grown up to button size.
                    shape = RoundedCornerShape(
                        topStart = 26.dp, topEnd = 14.dp, bottomEnd = 26.dp, bottomStart = 14.dp
                    ),
                    containerColor = Primary,
                    contentColor = OnPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New")
                }
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("New macro") },
                        onClick = { fabMenu = false; editorMacroId = null; showEditor = true }
                    )
                    DropdownMenuItem(
                        text = { Text("New folder") },
                        onClick = { fabMenu = false; showCreateFolder = true }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 8.dp)) {
            banner()
            // Header bar: normally the wordmark + live counter + a discreet search icon; tapping
            // the icon morphs the bar into a compact inline search field (no standalone pill field).
            if (searchOpen) {
                val focusRequester = remember { FocusRequester() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 6.dp, end = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Search, contentDescription = null,
                        tint = OnSurfaceVariant, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMono, fontSize = 15.sp, color = OnSurface
                        ),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) {
                                    Text(
                                        "Find a macro",
                                        fontFamily = JetBrainsMono, fontSize = 15.sp,
                                        color = OnSurfaceVariant
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    IconButton(
                        onClick = { query = ""; searchOpen = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close search", tint = OnSurface)
                    }
                }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                // Wordmark header with the app's heartbeat: the dot breathes while anything is armed.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 6.dp, end = 6.dp)
                ) {
                    PulseDot(alive = macros.any { it.enabled })
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "automatiq",
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                        color = OnSurface
                    )
                    Spacer(Modifier.weight(1f))
                    if (macros.isNotEmpty()) {
                        val active = macros.count { it.enabled }
                        Text(
                            "$active of ${macros.size} live",
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                        Spacer(Modifier.width(2.dp))
                        IconButton(onClick = { searchOpen = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Search, contentDescription = "Search macros",
                                tint = OnSurfaceVariant, modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Nothing automated yet.",
                            fontFamily = JetBrainsMono, fontSize = 16.sp, color = OnSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap + and teach your phone its first trick.",
                            color = OnSurfaceVariant
                        )
                    }
                }
            } else if (searching && visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing here called \"${query.trim()}\".", color = OnSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                ) {
                    if (searching) {
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
                                    dragHandleModifier = Modifier
                                )
                            }
                        }
                    } else {
                        items(rows, key = { it.key }) { row ->
                            ReorderableItem(reorderState, key = row.key) { _ ->
                                when (row) {
                                    is FolderRow -> FolderCard(
                                        folder = row.folder,
                                        memberCount = row.memberCount,
                                        activeCount = row.activeCount,
                                        switchOn = folderSwitchOn(row.memberCount, row.activeCount),
                                        onClick = { vm.onToggleExpanded(row.folder) },
                                        onToggleAll = { enabled -> vm.onToggleFolder(row.folder, enabled) },
                                        onRename = { renameTarget = row.folder },
                                        onDelete = { deleteTarget = row.folder },
                                        dragHandleModifier = Modifier.draggableHandle(onDragStopped = { dropped() })
                                    )
                                    is MacroRow -> {
                                        val member = row.macro.folderId != null
                                        val folderColor = folders.firstOrNull { it.id == row.macro.folderId }
                                            ?.let { Color(it.cardColor) }
                                        MacroCard(
                                            macro = row.macro,
                                            onClick = { editorMacroId = row.macro.id; showEditor = true },
                                            onDelete = { deleteMacro(row.macro) },
                                            onCopy = { vm.onCopy(row.macro) },
                                            onSend = { sendMacro(row.macro) },
                                            onToggle = { enabled -> vm.onToggle(row.macro, enabled) },
                                            veinColor = folderColor,
                                            onMoveToFolder = if (folders.isNotEmpty()) {
                                                { moveTarget = row.macro }
                                            } else null,
                                            onMoveToRoot = if (member) {
                                                { vm.onMoveToFolder(row.macro, null) }
                                            } else null,
                                            modifier = if (member) Modifier.padding(start = 16.dp) else Modifier,
                                            dragHandleModifier = Modifier.draggableHandle(onDragStopped = { dropped() })
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        val editingMacro: Macro? = macros.find { it.id == editorMacroId }
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

    if (showCreateFolder) FolderNameDialog(
        title = "New folder", initial = "",
        onConfirm = { vm.onCreateFolder(it); showCreateFolder = false },
        onDismiss = { showCreateFolder = false })

    renameTarget?.let { folder ->
        FolderNameDialog(
            title = "Rename folder", initial = folder.name,
            onConfirm = { vm.onRenameFolder(folder, it); renameTarget = null },
            onDismiss = { renameTarget = null })
    }

    deleteTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${folder.name}\"?") },
            text = { Text("The macros move out to the root — nothing is deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val members = vm.deleteFolder(folder)
                        val result = snackbar.showSnackbar("\"${folder.name}\" deleted", actionLabel = "Undo")
                        if (result == SnackbarResult.ActionPerformed) vm.onUndoDeleteFolder(folder, members)
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    moveTarget?.let { macro ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move \"${macro.name}\" to…") },
            text = {
                Column {
                    folders.forEach { folder ->
                        TextButton(onClick = { vm.onMoveToFolder(macro, folder.id); moveTarget = null }) {
                            Text(folder.name)
                        }
                    }
                    TextButton(onClick = { vm.onMoveToFolder(macro, null); moveTarget = null }) {
                        Text("Root (no folder)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancel") } }
        )
    }
}

/** A small status dot: breathes while at least one macro is armed, rests dim otherwise. */
@Composable
private fun PulseDot(alive: Boolean) {
    val alpha = if (alive) {
        val breath = rememberInfiniteTransition(label = "pulseDot")
        val a by breath.animateFloat(
            0.35f, 1f,
            infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulseDotAlpha"
        )
        a
    } else 0.3f
    Box(
        Modifier
            .size(9.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Primary)
    )
}

@Composable
private fun FolderNameDialog(
    title: String, initial: String,
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") }, singleLine = true
            )
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
