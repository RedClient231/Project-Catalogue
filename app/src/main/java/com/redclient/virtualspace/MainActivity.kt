package com.redclient.virtualspace

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.redclient.virtualspace.ui.screens.*
import com.redclient.virtualspace.ui.theme.VirtualSpaceTheme
import com.redclient.virtualspace.util.PermissionHandler
import java.io.File

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Apps", Icons.Default.Apps)
    object Import : Screen("import", "Import", Icons.Default.AddCircle)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Logs : Screen("logs", "Logs", Icons.Default.List)
}

class MainActivity : ComponentActivity() {

    private val permissionHandler by lazy { PermissionHandler(this) }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.entries.all { it.value }
        if (!allGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private val pickApk = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleApkImport(it) }
    }

    private val pickXapk = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleXapkImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkPermissions()
        handleIntent(intent)

        setContent {
            VirtualSpaceTheme {
                VirtualSpaceAppContent(
                    onPickApk = { pickApk.launch("application/vnd.android.package-archive") },
                    onPickXapk = { pickXapk.launch("application/octet-stream") }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun checkPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.REQUEST_INSTALL_PACKAGES
            )
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.REQUEST_INSTALL_PACKAGES
            )
        }
        requestPermissions.launch(permissions)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                intent.data?.let { handleApkImport(it) }
            }
        }
    }

    private fun handleApkImport(uri: Uri) {
        // Navigate to import screen with APK URI
        // Implementation handles copy + install flow
    }

    private fun handleXapkImport(uri: Uri) {
        // Handle XAPK import with OBB extraction
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualSpaceAppContent(
    onPickApk: () -> Unit,
    onPickXapk: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val screens = listOf(Screen.Home, Screen.Import, Screen.Logs, Screen.Settings)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen(navController) }
            composable(Screen.Import.route) {
                ImportScreen(
                    navController = navController,
                    onPickApk = onPickApk,
                    onPickXapk = onPickXapk
                )
            }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Logs.route) { LogsScreen() }
        }
    }
}
