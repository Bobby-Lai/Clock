package com.android.clock.ui.screen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.clock.R
import com.android.clock.data.model.ClockData
import com.android.clock.ui.viewmodel.ClockViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    viewModel: ClockViewModel
) {
    val context = LocalContext.current
    val clocks by viewModel.clocks.collectAsState()
    val availableTimeZones by viewModel.availableTimeZones.collectAsState()
    var showTimeZoneDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    val selectedClockData = remember { mutableStateListOf<ClockData>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting)) },
                actions = {
                    if (clocks.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                if (isEditMode) {
                                    // Delete selected clocks
                                    selectedClockData.forEach { clock ->
                                        viewModel.deleteClock(clock)
                                    }
                                    selectedClockData.clear()
                                }
                                isEditMode = !isEditMode
                            }
                        ) {
                            Text(
                                if (isEditMode) stringResource(R.string.delete) else stringResource(R.string.edit),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isEditMode) {
                FloatingActionButton(
                    onClick = { showTimeZoneDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (clocks.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Clock list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(clocks) { clock ->
                        ClockListItem(
                            clockData = clock,
                            isEditMode = isEditMode,
                            isSelected = selectedClockData.contains(clock),
                            onToggleSelection = {
                                if (selectedClockData.contains(clock)) {
                                    selectedClockData.remove(clock)
                                } else {
                                    selectedClockData.add(clock)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Timezone selection dialog
    if (showTimeZoneDialog) {
        TimeZoneSelectionDialog(
            availableTimeZones = availableTimeZones,
            onDismiss = { showTimeZoneDialog = false },
            onTimeZoneSelected = { timezone ->
                viewModel.addClock(timezone = timezone, name = timezone)
                showTimeZoneDialog = false
            },
            context = context
        )
    }
}

@Composable
fun ClockListItem(
    clockData: ClockData,
    isEditMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = {
                    if (isEditMode) {
                        onToggleSelection()
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isEditMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() }
                    )
                }
                
                Column {
                    Text(
                        text = clockData.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = clockData.timezone,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun TimeZoneSelectionDialog(
    availableTimeZones: List<String>,
    onDismiss: () -> Unit,
    onTimeZoneSelected: (String) -> Unit,
    context: Context
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (availableTimeZones.isEmpty()){
                        Toast.makeText(context, "Failed to fetch timezones", Toast.LENGTH_SHORT).show()
                    } else {
                        items(availableTimeZones) { timezone ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTimeZoneSelected(timezone) }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = timezone,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
} 