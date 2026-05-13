package com.redclient.virtualspace.data.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.redclient.virtualspace.data.model.VirtualApp
import com.redclient.virtualspace.data.model.VirtualActivity
import kotlinx.coroutines.flow.Flow

@Dao
interface VirtualAppDao {
    @Query("SELECT * FROM virtual_apps ORDER BY installTime DESC")
    fun getAllApps(): Flow<List<VirtualApp>>

    @Query("SELECT * FROM virtual_apps WHERE packageName = :pkg")
    suspend fun getApp(pkg: String): VirtualApp?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: VirtualApp)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<VirtualApp>)

    @Delete
    suspend fun deleteApp(app: VirtualApp)

    @Query("DELETE FROM virtual_apps WHERE packageName = :pkg")
    suspend fun deleteByPackage(pkg: String)

    @Query("UPDATE virtual_apps SET lastLaunchTime = :time, launchCount = launchCount + 1 WHERE packageName = :pkg")
    suspend fun recordLaunch(pkg: String, time: Long = System.currentTimeMillis())

    @Query("UPDATE virtual_apps SET dataSize = :size WHERE packageName = :pkg")
    suspend fun updateDataSize(pkg: String, size: Long)

    @Query("SELECT * FROM virtual_apps WHERE isGame = 1")
    fun getGames(): Flow<List<VirtualApp>>

    @Query("SELECT COUNT(*) FROM virtual_apps")
    fun getAppCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<VirtualActivity>)

    @Query("SELECT * FROM app_activities WHERE packageName = :pkg")
    suspend fun getActivities(pkg: String): List<VirtualActivity>

    @Query("DELETE FROM app_activities WHERE packageName = :pkg")
    suspend fun deleteActivities(pkg: String)
}
