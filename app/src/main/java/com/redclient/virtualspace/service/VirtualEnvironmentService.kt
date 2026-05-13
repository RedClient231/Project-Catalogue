package com.redclient.virtualspace.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.redclient.virtualspace.MainActivity
import com.redclient.virtualspace.R
import com.redclient.virtualspace.engine.NativeBridge

class VirtualEnvironmentService : Service() {

    private val binder = LocalBinder()
    private var isRunning = false

    inner class LocalBinder : Binder() {
        fun getService(): VirtualEnvironmentService = this@VirtualEnvironmentService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForeground(NOTIFICATION_ID, buildNotification())
            initializeVirtualEnvironment()
        }
        return START_STICKY
    }

    private fun initializeVirtualEnvironment() {
        val basePath = getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
        NativeBridge.initialize(basePath)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Virtual Environment",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VirtualSpace background virtualization service"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VirtualSpace Active")
            .setContentText("Virtual environment is running")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "virtual_env_service"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, VirtualEnvironmentService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VirtualEnvironmentService::class.java))
        }
    }
}
