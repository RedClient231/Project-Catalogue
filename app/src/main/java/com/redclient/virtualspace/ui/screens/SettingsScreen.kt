package com.redclient.virtualspace.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.redclient.virtualspace.viewmodel.SettingsViewModel
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val logSize by viewModel.logDirSize.collectAsState()
    val cacheSize by viewModel.cacheDirSize.collectAsState()

    LaunchedEffect(Unit) { viewModel.updateDirSizes() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // GameGuardian Compatibility
            SettingsSection(title = "GameGuardian") {
                SettingsSwitchItem(
                    icon = Icons.Default.Memory,
                    title = "GameGuardian Compatibility",
                    subtitle = "Enable memory hook interface for GameGuardian",
                    checked = state.ggCompatEnabled,
                    onCheckedChange = { viewModel.setGgCompat(it) }
                )
                if (state.ggCompatEnabled) {
                    Text(
                        "Memory hooks initialized. GameGuardian should now detect this virtual space.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }

            // ABI Selection
            SettingsSection(title = "Architecture") {
                var abiExpanded by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("ABI Selection") },
                    supportingContent = {
                        Text(
                            when (state.abiOverride) {
                                "auto" -> "Auto-detect (Recommended)"
                                "arm64-v8a" -> "ARM64 (64-bit)"
                                "armeabi-v7a" -> "ARM32 (32-bit)"
                                else -> "Auto"
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.DeveloperBoard, null) },
                    trailingContent = {
                        Box {
                            TextButton(onClick = { abiExpanded = true }) {
                                Text("Change")
                            }
                            DropdownMenu(
                                expanded = abiExpanded,
                                onDismissRequest = { abiExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Auto-detect") },
                                    onClick = {
                                        viewModel.setAbiOverride("auto")
                                        abiExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("ARM64 (64-bit)") },
                                    onClick = {
                                        viewModel.setAbiOverride("arm64-v8a")
                                        abiExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("ARM32 (32-bit)") },
                                    onClick = {
                                        viewModel.setAbiOverride("armeabi-v7a")
                                        abiExpanded = false
                                    }
                                )
                            }
                        }
                    }
                )
            }

            // Logcat
            SettingsSection(title = "Logging") {
                SettingsSwitchItem(
                    icon = Icons.Default.Terminal,
                    title = "Logcat Capture",
                    subtitle = "Capture and store logs to /Download/VirtualSpace/logs/",
                    checked = state.logcatEnabled,
                    onCheckedChange = { viewModel.setLogcatEnabled(it) }
                )
                ListItem(
                    headlineContent = { Text("Log Storage Size") },
                    supportingContent = { Text(formatBytes(logSize)) },
                    leadingContent = { Icon(Icons.Default.Storage, null) },
                    trailingContent = {
                        TextButton(onClick = { viewModel.clearLogs() }) {
                            Text("Clear")
                        }
                    }
                )
            }

            // Storage
            SettingsSection(title = "Storage") {
                ListItem(
                    headlineContent = { Text("Virtual Cache") },
                    supportingContent = { Text(formatBytes(cacheSize)) },
                    leadingContent = { Icon(Icons.Default.Cached, null) },
                    trailingContent = {
                        TextButton(onClick = {
                            viewModel.clearCache()
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Clear")
                        }
                    }
                )
            }

            // About
            SettingsSection(title = "About") {
                ListItem(
                    headlineContent = { Text("VirtualSpace") },
                    supportingContent = { Text("Version 1.0.0 | Built 2026") },
                    leadingContent = {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column { content() }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

private fun formatBytes(bytes: Long): String {
    val df = DecimalFormat("0.00")
    return when {
        bytes >= 1_073_741_824 -> "${df.format(bytes / 1_073_741_824.0)} GB"
        bytes >= 1_048_576 -> "${df.format(bytes / 1_048_576.0)} MB"
        bytes >= 1_024 -> "${df.format(bytes / 1_024.0)} KB"
        else -> "$bytes B"
    }
}
