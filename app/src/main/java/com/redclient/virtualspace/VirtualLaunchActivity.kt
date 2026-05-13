package com.redclient.virtualspace

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.redclient.virtualspace.engine.NativeBridge

/**
 * VirtualLaunchActivity - Entry point for launching apps inside the virtual space.
 * Handles the intent routing and initializes the native execution context.
 */
class VirtualLaunchActivity : Activity() {

    private val tag = "VirtualLaunch"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra("package_name")
        val installDir = intent.getStringExtra("install_dir")

        if (packageName == null || installDir == null) {
            Log.e(tag, "Missing package_name or install_dir")
            finish()
            return
        }

        Log.i(tag, "Launching virtual app: $packageName")

        try {
            // Initialize native hooks before launching
            NativeBridge.setupMemoryHooks()

            // Launch via native engine
            val success = NativeBridge.launchApp(packageName, installDir)
            if (!success) {
                Log.e(tag, "Native launch failed for $packageName")
            }
        } catch (e: Exception) {
            Log.e(tag, "Launch exception: ${e.message}")
        }

        // Finish immediately - the virtual app runs in its own context
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle runtime intent redirection for virtual apps
        val pkg = intent.getStringExtra("package_name")
        Log.d(tag, "New intent for: $pkg")
    }
}
