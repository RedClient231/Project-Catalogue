package com.redclient.virtualspace.viewmodel

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redclient.virtualspace.dataStore
import com.redclient.virtualspace.engine.NativeBridge
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class SettingsState(
    val ggCompatEnabled: Boolean = false,
    val logcatEnabled: Boolean = false,
    val abiOverride: String = "auto",
    val showSystemApps: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = application.dataStore

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _logDirSize = MutableStateFlow(0L)
    val logDirSize: StateFlow<Long> = _logDirSize.asStateFlow()

    private val _cacheDirSize = MutableStateFlow(0L)
    val cacheDirSize: StateFlow<Long> = _cacheDirSize.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _state.value = SettingsState(
                    ggCompatEnabled = prefs[GG_COMPAT_KEY] ?: false,
                    logcatEnabled = prefs[LOGCAT_KEY] ?: false,
                    abiOverride = prefs[ABI_KEY] ?: "auto",
                    showSystemApps = prefs[SHOW_SYSTEM_KEY] ?: false
                )
            }
        }
    }

    fun setGgCompat(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[GG_COMPAT_KEY] = enabled }
            if (enabled) {
                NativeBridge.enableGameGuardianCompat()
            }
        }
    }

    fun setLogcatEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[LOGCAT_KEY] = enabled }
        }
    }

    fun setAbiOverride(abi: String) {
        viewModelScope.launch {
            dataStore.edit { it[ABI_KEY] = abi }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val cacheDir = getApplication<Application>().cacheDir
            cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            updateDirSizes()
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            val logDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .resolve("VirtualSpace/logs")
            logDir.listFiles()?.forEach { it.deleteRecursively() }
            updateDirSizes()
        }
    }

    fun updateDirSizes() {
        viewModelScope.launch {
            _logDirSize.value = calculateDirSize(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .resolve("VirtualSpace/logs")
            )
            _cacheDirSize.value = calculateDirSize(getApplication<Application>().cacheDir)
        }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    companion object {
        private val GG_COMPAT_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("gg_compat")
        private val LOGCAT_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("logcat_enabled")
        private val ABI_KEY = androidx.datastore.preferences.core.stringPreferencesKey("abi_override")
        private val SHOW_SYSTEM_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("show_system_apps")
    }
}

private suspend fun androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>.edit(
    transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit
) {
    updateData {
        val mutable = it.toMutablePreferences()
        transform(mutable)
        mutable
    }
}
