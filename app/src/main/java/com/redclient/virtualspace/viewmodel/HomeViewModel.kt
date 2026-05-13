package com.redclient.virtualspace.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.redclient.virtualspace.VirtualSpaceApp
import com.redclient.virtualspace.data.model.VirtualApp
import com.redclient.virtualspace.data.repository.VirtualAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VirtualSpaceApp.instance.appRepository

    val installedApps: StateFlow<List<VirtualApp>> = repository.allApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appCount: StateFlow<Int> = repository.appCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _systemApps = MutableStateFlow<List<AppListItem>>(emptyList())
    val systemApps: StateFlow<List<AppListItem>> = _systemApps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun launchVirtualApp(app: VirtualApp) {
        viewModelScope.launch {
            repository.recordLaunch(app.packageName)
            // Trigger native launch via engine
        }
    }

    fun uninstallVirtualApp(packageName: String) {
        viewModelScope.launch {
            repository.uninstallApp(packageName)
        }
    }

    fun loadSystemApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val flags = PackageManager.GET_META_DATA
                val installed = pm.getInstalledApplications(flags)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                    .map {
                        AppListItem(
                            packageName = it.packageName,
                            appName = pm.getApplicationLabel(it).toString(),
                            icon = pm.getApplicationIcon(it),
                            isSystem = false
                        )
                    }
                    .sortedBy { it.appName.lowercase() }

                val installedPkgs = repository.allApps.firstOrNull()?.map { it.packageName } ?: emptyList()
                installed.filter { it.packageName !in installedPkgs }
            }
            _systemApps.value = apps
            _isLoading.value = false
        }
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    data class AppListItem(
        val packageName: String,
        val appName: String,
        val icon: Drawable,
        val isSystem: Boolean
    )
}
