package com.redclient.virtualspace.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.redclient.virtualspace.service.VirtualEnvironmentService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Auto-start virtual environment service on boot
            VirtualEnvironmentService.start(context)
        }
    }
}
