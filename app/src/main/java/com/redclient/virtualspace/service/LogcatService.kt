package com.redclient.virtualspace.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.redclient.virtualspace.MainActivity
import kotlinx.coroutines.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

class LogcatService : Service(), CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.IO + job

    private var captureJob: Job? = null
    private var isCapturing = false
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCapture()
        }
        return START_STICKY
    }

    private fun startCapture() {
        if (isCapturing) return
        isCapturing = true

        val outputDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "VirtualSpace/logs"
        )
        outputDir.mkdirs()

        startForeground(NOTIFICATION_ID, buildNotification(true))

        // Start native logcat capture
        nativeStartLogcatCapture(outputDir.absolutePath)

        captureJob = launch {
            val logFile = File(outputDir, "logcat_${dateFormat.format(Date())}.txt")
            writeLogHeader(logFile)

            try {
                Runtime.getRuntime().exec("logcat -c") // Clear buffer
                val process = Runtime.getRuntime().exec("logcat -v threadtime *:D")
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                FileOutputStream(logFile, true).use { output ->
                    val writer = BufferedWriter(OutputStreamWriter(output))
                    var line: String?
                    while (isCapturing && isActive) {
                        line = reader.readLine()
                        if (line != null) {
                            writer.write(line)
                            writer.newLine()
                            writer.flush()
                        }
                    }
                }
                process.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Logcat capture error: ${e.message}")
                // Fallback: capture app-specific logs
                captureAppLogs(logFile)
            }
        }
    }

    private fun captureAppLogs(logFile: File) {
        launch {
            try {
                val process = Runtime.getRuntime().exec("logcat -d -s VirtualSpaceJNI:D VirtualSpace:V AndroidRuntime:E")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                FileOutputStream(logFile, true).use { output ->
                    val writer = BufferedWriter(OutputStreamWriter(output))
                    reader.lineSequence().forEach { line ->
                        writer.write(line)
                        writer.newLine()
                    }
                    writer.flush()
                }
                process.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Fallback log capture failed: ${e.message}")
            }
        }
    }

    private fun stopCapture() {
        isCapturing = false
        captureJob?.cancel()
        captureJob = null
        nativeStopLogcatCapture()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun writeLogHeader(logFile: File) {
        logFile.writeText(
            buildString {
                appendLine("===== VirtualSpace Logcat =====")
                appendLine("Started: ${Date()}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("===============================")
                appendLine()
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Logcat Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Logcat capture to /Download/VirtualSpace/logs/"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(active: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LogcatService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Logcat Capture")
            .setContentText(if (active) "Capturing logs…" else "Logcat capture")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(active)
            .build()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private external fun nativeStartLogcatCapture(outputDir: String): Boolean
    private external fun nativeStopLogcatCapture()

    companion object {
        private const val TAG = "LogcatService"
        private const val CHANNEL_ID = "logcat_capture"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_START = "com.redclient.virtualspace.LOGCAT_START"
        const val ACTION_STOP = "com.redclient.virtualspace.LOGCAT_STOP"

        fun start(context: Context) {
            val intent = Intent(context, LogcatService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LogcatService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
