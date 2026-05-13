package com.redclient.virtualspace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "virtual_apps")
@Serializable
data class VirtualApp(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val sourcePath: String = "",
    val installPath: String = "",
    val iconPath: String = "",
    val nativeLibDir: String = "",
    val abi: String = "auto",
    val isGame: Boolean = false,
    val is32Bit: Boolean = false,
    val is64Bit: Boolean = false,
    val installTime: Long = System.currentTimeMillis(),
    val lastLaunchTime: Long = 0,
    val launchCount: Int = 0,
    val hasNativeLibs: Boolean = false,
    val permissions: String = "[]",
    val dataSize: Long = 0,
    val cacheSize: Long = 0,
    val isXapk: Boolean = false,
    val obbPath: String = ""
)

@Entity(tableName = "app_activities")
@Serializable
data class VirtualActivity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val activityName: String,
    val isMain: Boolean = false,
    val isExported: Boolean = false
)

@Serializable
data class ApkMetaInfo(
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val label: String,
    val permissions: List<String>,
    val activities: List<ActivityMeta>,
    val hasNativeLibs: Boolean,
    val minSdk: Int,
    val targetSdk: Int,
    val abiFilters: List<String>
)

@Serializable
data class ActivityMeta(
    val name: String,
    val exported: Boolean,
    val main: Boolean
)
