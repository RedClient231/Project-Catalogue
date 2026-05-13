package com.redclient.virtualspace.data.repository

import com.redclient.virtualspace.data.database.VirtualAppDao
import com.redclient.virtualspace.data.model.VirtualApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class VirtualAppRepository(private val dao: VirtualAppDao) {
    val allApps: Flow<List<VirtualApp>> = dao.getAllApps()
    val appCount: Flow<Int> = dao.getAppCount()
    val games: Flow<List<VirtualApp>> = dao.getGames()

    suspend fun getApp(packageName: String): VirtualApp? = dao.getApp(packageName)

    suspend fun installApp(app: VirtualApp) = dao.insertApp(app)

    suspend fun uninstallApp(packageName: String) = dao.deleteByPackage(packageName)

    suspend fun recordLaunch(packageName: String) = dao.recordLaunch(packageName)

    suspend fun isInstalled(packageName: String): Boolean {
        return dao.getApp(packageName) != null
    }

    suspend fun updateDataSize(packageName: String, size: Long) {
        dao.updateDataSize(packageName, size)
    }
}
