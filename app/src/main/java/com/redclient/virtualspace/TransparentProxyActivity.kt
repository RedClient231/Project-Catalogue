package com.redclient.virtualspace

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.redclient.virtualspace.engine.VirtualEngine
import java.io.File

/**
 * Transparent proxy activity that hosts virtualized app execution.
 * Creates an isolated execution context and forwards lifecycle events.
 */
class TransparentProxyActivity : Activity() {

    private val tag = "TransparentProxy"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra("package_name") ?: run {
            finish()
            return
        }
        val installPath = intent.getStringExtra("install_path") ?: ""
        val dataPath = intent.getStringExtra("data_path") ?: ""
        val libPath = intent.getStringExtra("lib_path") ?: ""

        Log.i(tag, "Proxying $packageName")
        Log.i(tag, "Install: $installPath, Data: $dataPath, Libs: $libPath")

        // Initialize the virtual engine for this app context
        val engine = VirtualEngine.getInstance(this)

        // Setup resource overrides and launch the virtual app's main activity
        launchVirtualApp(packageName, installPath, dataPath, libPath)

        // Keep this activity alive but invisible (transparent theme)
        // It maintains the process context for the virtualized app
    }

    private fun launchVirtualApp(packageName: String, installPath: String, dataPath: String, libPath: String) {
        try {
            // Load the virtual app's APK
            val dexPath = File(installPath).listFiles()?.find { it.name.endsWith(".apk") }
                ?: File(installPath, "base.apk")

            if (!dexPath.exists()) {
                Log.e(tag, "APK not found at $installPath")
                finish()
                return
            }

            // Create optimized dex directory
            val optDir = File(codeCacheDir, "opt_$packageName")
            optDir.mkdirs()

            // Build ClassLoader for the virtual app
            val vClassLoader = dalvik.system.DexClassLoader(
                dexPath.absolutePath,
                optDir.absolutePath,
                libPath,
                classLoader
            )

            // Attempt to find and launch the main activity
            // In production, this would parse the manifest and launch the correct activity
            Log.i(tag, "Virtual ClassLoader created for $packageName")

            // Notify that the app is launched
            // The actual rendering happens within this process using the virtual ClassLoader

        } catch (e: Exception) {
            Log.e(tag, "Failed to launch virtual app", e)
        }
    }

    override fun onPause() {
        super.onPause()
        // Forward pause to virtual app
    }

    override fun onResume() {
        super.onResume()
        // Forward resume to virtual app
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up virtual app context
        intent.getStringExtra("package_name")?.let { pkg ->
            VirtualEngine.getInstance(this).kill(pkg)
        }
    }

    override fun onBackPressed() {
        // Forward back press to virtual app
        finish()
    }
}


