package com.vibeactions.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibeactions.domain.model.TriggerType
import com.vibeactions.util.TEMPLATE_TOKENS
import com.vibeactions.util.isValidPhone
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    macroId: String?,
    onDone: () -> Unit,
    vm: MacroEditorViewModel = hiltViewModel()
) {
    LaunchedEffect(macroId) { vm.load(macroId) }
    val s by vm.state.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var intervalExpanded by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (macroId == null) "New Macro" else "Edit Macro") },
            navigationIcon = { TextButton(onClick = onDone) { Text("Cancel") } },
            actions = { TextButton(enabled = s.canSave, onClick = { vm.save(onDone) }) { Text("Save") } }
        )
    }) { p ->
        Column(
            Modifier
                .padding(p)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            OutlinedTextField(
                value = s.name, onValueChange = { v -> vm.update { it.copy(name = v) } },
                label = { Text("Name") }, isError = s.name.isNotEmpty() && !s.nameValid,
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TriggerType.entries.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = s.triggerType == type,
                        onClick = { vm.update { it.copy(triggerType = type) } },
                        shape = SegmentedButtonDefaults.itemShape(i, TriggerType.entries.size)
                    ) { Text(type.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }
                }
            }

            if (s.triggerType == TriggerType.SCHEDULED) {
                OutlinedButton(onClick = { showTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Time: ${s.scheduledTime}")
                }

                Text("Repeat on", style = MaterialTheme.typography.labelLarge)
                // Single letters (Mon→Sun) so all seven fit across; fixed order disambiguates T/T, S/S.
                val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..7).forEach { day ->
                        val selected = s.daysOfWeek.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                vm.update {
                                    val next = if (selected) it.daysOfWeek - day else it.daysOfWeek + day
                                    it.copy(daysOfWeek = next)
                                }
                            },
                            label = {
                                Text(
                                    dayLabels[day - 1],
                                    maxLines = 1,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (!s.daysValid) {
                    Text(
                        "Pick at least one day",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = intervalExpanded,
                    onExpandedChange = { intervalExpanded = it }
                ) {
                    OutlinedTextField(
                        value = intervalLabel(s.weekInterval),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Repeat") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = intervalExpanded,
                        onDismissRequest = { intervalExpanded = false }
                    ) {
                        (1..4).forEach { n ->
                            DropdownMenuItem(
                                text = { Text(intervalLabel(n)) },
                                onClick = {
                                    vm.update { st ->
                                        st.copy(
                                            weekInterval = n,
                                            startEpochDay = if (n > 1 && st.startEpochDay == null)
                                                LocalDate.now().toEpochDay() else st.startEpochDay
                                        )
                                    }
                                    intervalExpanded = false
                                }
                            )
                        }
                    }
                }

                if (s.weekInterval > 1) {
                    val startDay = s.startEpochDay ?: LocalDate.now().toEpochDay()
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Starts: ${LocalDate.ofEpochDay(startDay)}")
                    }
                }
            }

            Text("Recipients", style = MaterialTheme.typography.labelLarge)
            s.recipients.forEachIndexed { index, number ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = number,
                        onValueChange = { v ->
                            vm.update { st ->
                                st.copy(recipients = st.recipients.toMutableList().also { it[index] = v })
                            }
                        },
                        label = { Text("Number ${index + 1}") },
                        isError = number.isNotBlank() && !isValidPhone(number),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    if (s.recipients.size > 1) {
                        IconButton(onClick = {
                            vm.update { st ->
                                st.copy(recipients = st.recipients.filterIndexed { i, _ -> i != index })
                            }
                        }) { Icon(Icons.Default.Close, contentDescription = "Remove recipient") }
                    }
                }
            }
            TextButton(onClick = { vm.update { st -> st.copy(recipients = st.recipients + "") } }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add recipient")
            }
            if (!s.phoneValid && s.recipients.any { it.isNotBlank() }) {
                Text(
                    "Enter at least one valid number",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = s.message, onValueChange = { v -> vm.update { it.copy(message = v) } },
                label = { Text("Message") },
                supportingText = {
                    Text("${s.message.length} chars · variables: ${TEMPLATE_TOKENS.joinToString(" ")}")
                },
                minLines = 3, modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(checked = s.enabled, onCheckedChange = { v -> vm.update { it.copy(enabled = v) } })
            }
        }
    }

    if (showTime) {
        val parts = s.scheduledTime.split(":")
        val tps = rememberTimePickerState(
            initialHour = parts[0].toInt(), initialMinute = parts[1].toInt(), is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.update { it.copy(scheduledTime = "%02d:%02d".format(tps.hour, tps.minute)) }
                    showTime = false
                }) { Text("OK") }
            },
            text = { TimePicker(state = tps) }
        )
    }

    if (showDate) {
        val initMs = (s.startEpochDay ?: LocalDate.now().toEpochDay()) * 86_400_000L
        val dps = rememberDatePickerState(initialSelectedDateMillis = initMs)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dps.selectedDateMillis?.let { ms ->
                        vm.update { it.copy(startEpochDay = ms / 86_400_000L) }
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state = dps) }
    }
}

private fun intervalLabel(n: Int): String = if (n <= 1) "Every week" else "Every $n weeks"
