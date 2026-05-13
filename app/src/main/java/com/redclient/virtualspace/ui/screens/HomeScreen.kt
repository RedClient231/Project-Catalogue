package com.redclient.virtualspace.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.redclient.virtualspace.data.model.VirtualApp
import com.redclient.virtualspace.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val apps by viewModel.installedApps.collectAsState()
    val appCount by viewModel.appCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VirtualSpace", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "$appCount apps installed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                actions = {
                    IconButton(onClick = {
                        // Refresh list
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (apps.isEmpty()) {
                EmptyState(
                    onAddClick = { navController.navigate("import") }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppCard(
                            app = app,
                            onLaunch = { viewModel.launchVirtualApp(app) },
                            onUninstall = { viewModel.uninstallVirtualApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No virtual apps installed",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tap + below to add apps to your virtual space",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Apps")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCard(
    app: VirtualApp,
    onLaunch: () -> Unit,
    onUninstall: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        onClick = onLaunch,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            // App icon placeholder
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (app.iconPath.isNotEmpty()) {
                    AsyncImage(
                        model = app.iconPath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (app.isGame) Icons.Default.VideogameAsset else Icons.Default.Android,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = app.appName,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Text(
                text = app.versionName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (app.is64Bit) {
                    AssistChip(
                        onClick = {},
                        label = { Text("64", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(20.dp)
                    )
                }
                if (app.is32Bit) {
                    AssistChip(
                        onClick = {},
                        label = { Text("32", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(20.dp)
                    )
                }
                if (app.isGame) {
                    AssistChip(
                        onClick = {},
                        label = { Text("GAME", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(20.dp)
                    )
                }
            }

            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Launch") },
                    onClick = {
                        showMenu = false
                        onLaunch()
                    },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null) }
                )
                DropdownMenuItem(
                    text = { Text("Create Shortcut") },
                    onClick = {
                        showMenu = false
                        Toast.makeText(context, "Shortcut created", Toast.LENGTH_SHORT).show()
                    },
                    leadingIcon = { Icon(Icons.Default.AddLink, null) }
                )
                DropdownMenuItem(
                    text = { Text("App Info") },
                    onClick = { showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Info, null) }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Uninstall", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onUninstall()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, tint = MaterialTheme.colorScheme.error, contentDescription = null) }
                )
            }
        }
    }
}
