package com.redclient.virtualspace.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redclient.virtualspace.VirtualSpaceApp
import com.redclient.virtualspace.data.model.VirtualApp
import com.redclient.virtualspace.engine.NativeBridge
import com.redclient.virtualspace.util.ApkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VirtualSpaceApp.instance.appRepository

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState

    fun importApk(uri: Uri, context: Context) {
        viewModelScope.launch {
            _installState.value = InstallState.Loading("Copying APK…")

            val result = withContext(Dispatchers.IO) {
                try {
                    val tempFile = copyUriToTemp(uri, context, ".apk") ?: return@withContext Result.failure(Exception("Failed to copy APK"))

                    _installState.value = InstallState.Loading("Parsing APK…")
                    val apkInfo = ApkParser.parseApk(context, tempFile)

                    if (repository.isInstalled(apkInfo.packageName)) {
                        return@withContext Result.failure(Exception("App already installed in virtual space"))
                    }

                    _installState.value = InstallState.Loading("Installing…")
                    val installDir = File(context.getExternalFilesDir(null), "apps/${apkInfo.packageName}")
                    installDir.mkdirs()

                    // Install via native engine
                    val success = NativeBridge.installApk(tempFile.absolutePath, installDir.absolutePath)
                    if (!success) {
                        return@withContext Result.failure(Exception("Native installation failed"))
                    }

                    val virtualApp = VirtualApp(
                        packageName = apkInfo.packageName,
                        appName = apkInfo.label,
                        versionCode = apkInfo.versionCode,
                        versionName = apkInfo.versionName,
                        sourcePath = tempFile.absolutePath,
                        installPath = installDir.absolutePath,
                        hasNativeLibs = apkInfo.hasNativeLibs,
                        is64Bit = apkInfo.abiFilters.any { it.contains("64") },
                        is32Bit = apkInfo.abiFilters.any { it.contains("32") || it.contains("v7") },
                        isGame = apkInfo.isGame,
                        permissions = apkInfo.permissions.toString()
                    )

                    repository.installApp(virtualApp)
                    Result.success(virtualApp)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            _installState.value = result.fold(
                onSuccess = { InstallState.Success(it) },
                onFailure = { InstallState.Error(it.message ?: "Unknown error") }
            )
        }
    }

    fun importXapk(uri: Uri, context: Context) {
        viewModelScope.launch {
            _installState.value = InstallState.Loading("Extracting XAPK…")

            withContext(Dispatchers.IO) {
                try {
                    val tempFile = copyUriToTemp(uri, context, ".xapk") ?: return@withContext
                    val extractDir = File(context.cacheDir, "xapk_extract_${System.currentTimeMillis()}")
                    extractDir.mkdirs()

                    // Extract XAPK (ZIP containing APK + OBB)
                    val xapkInfo = ApkParser.parseXapk(tempFile, extractDir)

                    xapkInfo.baseApk?.let { baseApk ->
                        importApk(Uri.fromFile(baseApk), context)
                    }

                    // Copy OBB files
                    xapkInfo.obbFiles.forEach { obb ->
                        val obbDir = File(Environment.getExternalStorageDirectory(),
                            "Android/obb/${xapkInfo.packageName}")
                        obbDir.mkdirs()
                        obb.copyTo(File(obbDir, obb.name), overwrite = true)
                    }
                } catch (e: Exception) {
                    _installState.value = InstallState.Error(e.message ?: "XAPK extraction failed")
                }
            }
        }
    }

    fun cloneInstalledApp(packageName: String, context: Context) {
        viewModelScope.launch {
            _installState.value = InstallState.Loading("Cloning app…")

            withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    val sourceApk = File(appInfo.sourceDir)

                    val label = pm.getApplicationLabel(appInfo).toString()
                    val installDir = File(context.getExternalFilesDir(null), "apps/$packageName")
                    installDir.mkdirs()

                    val success = NativeBridge.installApk(sourceApk.absolutePath, installDir.absolutePath)

                    if (success) {
                        val virtualApp = VirtualApp(
                            packageName = packageName,
                            appName = label,
                            sourcePath = sourceApk.absolutePath,
                            installPath = installDir.absolutePath,
                            hasNativeLibs = File(appInfo.nativeLibraryDir).list()?.isNotEmpty() ?: false
                        )
                        repository.installApp(virtualApp)
                        _installState.value = InstallState.Success(virtualApp)
                    } else {
                        _installState.value = InstallState.Error("Failed to clone app")
                    }
                } catch (e: Exception) {
                    _installState.value = InstallState.Error(e.message ?: "Clone failed")
                }
            }
        }
    }

    fun resetState() {
        _installState.value = InstallState.Idle
    }

    private fun copyUriToTemp(uri: Uri, context: Context, suffix: String): File? {
        return try {
            val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}$suffix")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    sealed class InstallState {
        object Idle : InstallState()
        data class Loading(val message: String) : InstallState()
        data class Success(val app: VirtualApp) : InstallState()
        data class Error(val message: String) : InstallState()
    }
}
