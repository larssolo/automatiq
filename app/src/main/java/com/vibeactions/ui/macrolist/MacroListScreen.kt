package com.vibeactions.ui.macrolist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.ui.common.MacroCard
import com.vibeactions.ui.theme.OnSurfaceVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroListScreen(
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    banner: @Composable () -> Unit = {},
    vm: MacroListViewModel = hiltViewModel()
) {
    val macros by vm.macros.collectAsStateWithLifecycle()
    Scaffold(
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
            if (macros.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No macros yet.\nTap “New Macro” to create your first.",
                        color = OnSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)) {
                    items(macros, key = { it.id }) { macro ->
                        MacroCard(
                            macro = macro,
                            onToggle = { vm.onToggle(macro, it) },
                            onTap = { vm.onTrigger(macro) },
                            onEdit = { onEdit(macro.id) }
                        )
                    }
                }
            }
        }
    }
}
