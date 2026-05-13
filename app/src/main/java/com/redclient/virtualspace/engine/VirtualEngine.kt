package com.redclient.virtualspace.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.redclient.virtualspace.TransparentProxyActivity
import com.redclient.virtualspace.data.model.VirtualApp
import dalvik.system.DexClassLoader
import java.io.File

class VirtualEngine(private val context: Context) {

    private val tag = "VirtualEngine"
    private val activeApps = mutableMapOf<String, AppContext>()

    data class AppContext(
        val virtualApp: VirtualApp,
        val classLoader: DexClassLoader?,
        val dataDir: File,
        val libDir: File
    )

    /**
     * Launch a virtualized app by creating an isolated ClassLoader
     * and starting its main activity through a proxy.
     */
    fun launch(virtualApp: VirtualApp): Boolean {
        return try {
            val installDir = File(virtualApp.installPath)
            if (!installDir.exists()) {
                Log.e(tag, "Install directory not found: ${virtualApp.installPath}")
                return false
            }

            // Find the base APK
            val baseApk = File(virtualApp.sourcePath)
            if (!baseApk.exists()) {
                Log.e(tag, "APK not found: ${virtualApp.sourcePath}")
                return false
            }

            // Setup data directories
            val dataDir = File(context.getExternalFilesDir(null), "data/${virtualApp.packageName}")
            dataDir.mkdirs()

            val libDir = if (virtualApp.nativeLibDir.isNotEmpty()) {
                File(virtualApp.nativeLibDir)
            } else {
                File(installDir, "lib")
            }
            libDir.mkdirs()

            // Create optimized dex directory
            val optDir = File(context.codeCacheDir, "virtual_${virtualApp.packageName}")
            optDir.mkdirs()

            // Build ClassLoader chain
            val parentLoader = context.classLoader
            val classLoader = DexClassLoader(
                baseApk.absolutePath,
                optDir.absolutePath,
                libDir.absolutePath,
                parentLoader
            )

            val appCtx = AppContext(virtualApp, classLoader, dataDir, libDir)
            activeApps[virtualApp.packageName] = appCtx

            // Launch via proxy activity
            val launchIntent = Intent(context, TransparentProxyActivity::class.java).apply {
                putExtra("package_name", virtualApp.packageName)
                putExtra("install_path", virtualApp.installPath)
                putExtra("data_path", dataDir.absolutePath)
                putExtra("lib_path", libDir.absolutePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)

            Log.i(tag, "Launched ${virtualApp.packageName} in virtual space")
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch ${virtualApp.packageName}", e)
            false
        }
    }

    /**
     * Kill a running virtual app
     */
    fun kill(packageName: String) {
        activeApps.remove(packageName)
        Log.i(tag, "Killed virtual app: $packageName")
    }

    /**
     * Get active virtual app context
     */
    fun getAppContext(packageName: String): AppContext? =
        activeApps[packageName]

    /**
     * Check if a virtual app is currently running
     */
    fun isRunning(packageName: String): Boolean =
        activeApps.containsKey(packageName)

    /**
     * Clean up all active virtual apps
     */
    fun shutdown() {
        activeApps.clear()
        Log.i(tag, "Virtual engine shutdown")
    }

    companion object {
        @Volatile
        private var instance: VirtualEngine? = null

        fun getInstance(context: Context): VirtualEngine {
            return instance ?: synchronized(this) {
                instance ?: VirtualEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
