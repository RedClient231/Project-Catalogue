package com.redclient.virtualspace.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.redclient.virtualspace.MainActivity
import com.redclient.virtualspace.engine.NativeBridge
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.CoroutineContext

class AppInstallerService : Service(), CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apkPath = intent?.getStringExtra("apk_path") ?: return START_NOT_STICKY
        val installDir = intent.getStringExtra("install_dir") ?: return START_NOT_STICKY
        val isXapk = intent.getBooleanExtra("is_xapk", false)

        startForeground(NOTIFICATION_ID, buildNotification("Installing app…"))

        launch {
            try {
                val success = if (isXapk) {
                    NativeBridge.installXapk(apkPath, installDir)
                } else {
                    NativeBridge.installApk(apkPath, installDir)
                }

                if (success) {
                    updateNotification("Installation complete", true)
                } else {
                    updateNotification("Installation failed", false)
                }
            } catch (e: Exception) {
                updateNotification("Error: ${e.message}", false)
            }

            delay(3000)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Installation",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VirtualSpace Installer")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentIntent(pendingIntent)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String, success: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VirtualSpace Installer")
            .setContentText(text)
            .setSmallIcon(if (success) android.R.drawable.ic_menu_save else android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "app_installer"
        private const val NOTIFICATION_ID = 1003

        fun install(context: Context, apkPath: String, installDir: String, isXapk: Boolean = false) {
            val intent = Intent(context, AppInstallerService::class.java).apply {
                putExtra("apk_path", apkPath)
                putExtra("install_dir", installDir)
                putExtra("is_xapk", isXapk)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
