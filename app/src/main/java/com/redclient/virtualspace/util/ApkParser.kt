package com.redclient.virtualspace.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.redclient.virtualspace.data.model.ApkMetaInfo
import com.redclient.virtualspace.engine.NativeBridge
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class ParsedApk(
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val label: String,
    val hasNativeLibs: Boolean,
    val abiFilters: List<String>,
    val permissions: List<String> = emptyList(),
    val mainActivity: String? = null,
    val isGame: Boolean = false
)

data class XapkInfo(
    val packageName: String,
    val baseApk: File?,
    val obbFiles: List<File>,
    val configApks: List<File>
)

object ApkParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseApk(context: Context, apkFile: File): ParsedApk {
        // Try native Rust parser first
        val nativeJson = NativeBridge.parseApkInfo(apkFile.absolutePath)
        if (nativeJson != null) {
            try {
                val meta = json.decodeFromString<ApkMetaInfo>(nativeJson)
                return ParsedApk(
                    packageName = meta.packageName,
                    versionCode = meta.versionCode,
                    versionName = meta.versionName,
                    label = meta.label,
                    hasNativeLibs = meta.hasNativeLibs,
                    abiFilters = meta.abiFilters,
                    permissions = meta.permissions,
                    isGame = meta.permissions.any { it.contains("GAME") || it.contains("game") }
                )
            } catch (_: Exception) {
                // Fallback to Android PackageManager
            }
        }

        // Android PackageManager fallback
        return parseViaPackageManager(context, apkFile)
    }

    private fun parseViaPackageManager(context: Context, apkFile: File): ParsedApk {
        val pm = context.packageManager

        @Suppress("DEPRECATION")
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(
                PackageManager.GET_ACTIVITIES.toLong() or
                PackageManager.GET_PERMISSIONS.toLong() or
                PackageManager.GET_META_DATA.toLong()
            ))
        } else {
            pm.getPackageArchiveInfo(apkFile.absolutePath,
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_PERMISSIONS or
                PackageManager.GET_META_DATA
            )
        } ?: return fallbackParse(apkFile)

        val appInfo = packageInfo.applicationInfo
        val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: apkFile.name

        val abiFilters = detectAbis(apkFile)

        @Suppress("DEPRECATION")
        return ParsedApk(
            packageName = packageInfo.packageName,
            versionCode = packageInfo.versionCode,
            versionName = packageInfo.versionName ?: "1.0",
            label = label,
            hasNativeLibs = abiFilters.isNotEmpty(),
            abiFilters = abiFilters,
            permissions = packageInfo.requestedPermissions?.toList() ?: emptyList(),
            isGame = appInfo?.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
        )
    }

    private fun fallbackParse(apkFile: File): ParsedApk {
        // Extract basic info from filename as last resort
        val name = apkFile.nameWithoutExtension
        return ParsedApk(
            packageName = "com.app.$name",
            versionCode = 1,
            versionName = "1.0",
            label = name,
            hasNativeLibs = false,
            abiFilters = emptyList()
        )
    }

    fun parseXapk(xapkFile: File, extractDir: File): XapkInfo {
        ZipFile(xapkFile).use { zip ->
            zip.extractAll(extractDir.absolutePath)
        }

        // Read manifest.json if present
        val manifestFile = File(extractDir, "manifest.json")
        var packageName = ""
        if (manifestFile.exists()) {
            try {
                val manifest = json.parseToJsonElement(manifestFile.readText())
                // Extract package name from manifest
            } catch (_: Exception) {}
        }

        val baseApk = extractDir.listFiles()?.find { it.name == "base.apk" || it.extension == "apk" }
        val obbFiles = extractDir.walkTopDown()
            .filter { it.extension == "obb" }
            .toList()
        val configApks = extractDir.listFiles()
            ?.filter { it.extension == "apk" && it != baseApk }
            ?: emptyList()

        if (packageName.isEmpty() && baseApk != null) {
            // Parse base APK to get package name
        }

        return XapkInfo(packageName, baseApk, obbFiles, configApks)
    }

    private fun detectAbis(apkFile: File): List<String> {
        val abis = mutableSetOf<String>()
        try {
            ZipInputStream(FileInputStream(apkFile)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    val name = entry!!.name
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        val parts = name.split("/")
                        if (parts.size >= 2) {
                            abis.add(parts[1])
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return abis.toList()
    }

    fun ParsedApk.isGame(): Boolean {
        return isGame || permissions.any {
            it.contains("GAME") || it.contains("BILLING")
        }
    }
}
