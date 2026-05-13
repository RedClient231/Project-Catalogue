package com.redclient.virtualspace.ui.screens

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.redclient.virtualspace.viewmodel.HomeViewModel
import com.redclient.virtualspace.viewmodel.ImportViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    navController: NavController,
    onPickApk: () -> Unit,
    onPickXapk: () -> Unit,
    viewModel: ImportViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val installState by viewModel.installState.collectAsState()
    val systemApps by homeViewModel.systemApps.collectAsState()
    val searchQuery by homeViewModel.searchQuery.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.loadSystemApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Apps") },
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
        ) {
            // Import actions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Add to Virtual Space",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ImportButton(
                            icon = Icons.Default.InsertDriveFile,
                            label = "Import APK",
                            onClick = onPickApk,
                            modifier = Modifier.weight(1f)
                        )
                        ImportButton(
                            icon = Icons.Default.FolderZip,
                            label = "Import XAPK",
                            onClick = onPickXapk,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Install status
            AnimatedVisibility(
                visible = installState !is ImportViewModel.InstallState.Idle,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                when (val state = installState) {
                    is ImportViewModel.InstallState.Loading -> {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    is ImportViewModel.InstallState.Success -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("${state.app.appName} installed successfully")
                            }
                        }
                        LaunchedEffect(Unit) {
                            viewModel.resetState()
                        }
                    }
                    is ImportViewModel.InstallState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(state.message)
                            }
                        }
                        LaunchedEffect(Unit) {
                            viewModel.resetState()
                        }
                    }
                    else -> {}
                }
            }

            // Divider
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Clone installed apps section
            Text(
                "Clone Installed App",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { homeViewModel.updateSearch(it) },
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                val filtered = systemApps.filter {
                    searchQuery.isEmpty() ||
                    it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }

                items(filtered, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = { Text(app.appName) },
                        supportingContent = { Text(app.packageName) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Android,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    viewModel.cloneInstalledApp(app.packageName, context)
                                }
                            ) {
                                Text("Clone")
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun ImportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
