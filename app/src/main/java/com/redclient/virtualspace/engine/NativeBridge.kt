package com.redclient.virtualspace.engine

import android.util.Log

object NativeBridge {

    private const val TAG = "NativeBridge"
    private var initialized = false

    init {
        try {
            System.loadLibrary("virtualspace")
            Log.i(TAG, "Native library loaded: virtualspace")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load virtualspace library", e)
            // Fallback to vsnative (CMake-built)
            try {
                System.loadLibrary("vsnative")
                Log.i(TAG, "Native library loaded: vsnative")
            } catch (e2: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load vsnative library", e2)
            }
        }
    }

    fun initialize(basePath: String): Boolean {
        if (initialized) return true
        return try {
            val result = nativeInitVirtualEnv(basePath)
            initialized = result
            Log.i(TAG, "Native bridge initialized: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Native init failed", e)
            false
        }
    }

    fun installApk(apkPath: String, installDir: String): Boolean =
        nativeInstallApk(apkPath, installDir)

    fun installXapk(xapkPath: String, installDir: String): Boolean =
        nativeInstallXapk(xapkPath, installDir)

    fun launchApp(packageName: String, installDir: String): Boolean =
        nativeLaunchApp(packageName, installDir)

    fun setupMemoryHooks(): Boolean =
        nativeSetupMemoryHooks()

    fun enableGameGuardianCompat(): Boolean {
        return try {
            val result = nativeEnableGameGuardianCompat()
            Log.i(TAG, "GameGuardian compatibility enabled: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable GG compat", e)
            false
        }
    }

    fun getNativeLibDir(apkPath: String): String? =
        nativeGetNativeLibDir(apkPath)

    fun extractNativeLibs(apkPath: String, outputDir: String, abi: String): Boolean =
        nativeExtractNativeLibs(apkPath, outputDir, abi)

    fun parseApkInfo(apkPath: String): String? =
        nativeParseApkInfo(apkPath)

    fun getVirtualMaps(): String? =
        nativeGetVirtualMaps()

    // JNI native methods
    private external fun nativeInitVirtualEnv(basePath: String): Boolean
    private external fun nativeInstallApk(apkPath: String, installDir: String): Boolean
    private external fun nativeInstallXapk(xapkPath: String, installDir: String): Boolean
    private external fun nativeLaunchApp(packageName: String, installDir: String): Boolean
    private external fun nativeSetupMemoryHooks(): Boolean
    private external fun nativeEnableGameGuardianCompat(): Boolean
    private external fun nativeGetNativeLibDir(apkPath: String): String?
    private external fun nativeExtractNativeLibs(apkPath: String, outputDir: String, abi: String): Boolean
    private external fun nativeParseApkInfo(apkPath: String): String?
    private external fun nativeGetVirtualMaps(): String?
}
