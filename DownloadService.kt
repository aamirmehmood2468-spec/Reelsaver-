package com.reelsaver.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.reelsaver.R
import com.reelsaver.util.Prefs
import com.reelsaver.util.YtDlpRunner
import kotlinx.coroutines.*
import java.io.File

class DownloadService : Service() {

    companion object {
        const val EXTRA_URL = "url"
        private const val TAG = "ReelSaver"
        private const val CHANNEL_ID = "reelsaver_download"
        private const val CHANNEL_NAME = "ReelSaver Downloads"
        private const val NOTIF_ID_PROGRESS = 1001
        private const val NOTIF_ID_DONE = 1002
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID_PROGRESS, buildProgressNotification("Starting download…"))

        scope.launch {
            try {
                downloadReel(url, startId)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                showErrorNotification(e.message ?: "Unknown error")
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadReel(url: String, startId: Int) {
        val prefs = Prefs(applicationContext)
        val saveDir = prefs.getSaveDirectory()

        val dir = File(saveDir)
        if (!dir.exists()) dir.mkdirs()

        updateProgressNotification("Downloading reel…")

        val result = YtDlpRunner(applicationContext).download(
            url = url,
            outputDir = saveDir,
            quality = prefs.getQuality(),
            onProgress = { progress ->
                updateProgressNotification("Downloading… $progress%")
            }
        )

        if (result.success) {
            showSuccessNotification(result.fileName ?: "reel")
        } else {
            showErrorNotification(result.error ?: "Download failed")
        }

        stopSelf(startId)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildProgressNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ReelSaver")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateProgressNotification(text: String) =
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID_PROGRESS, buildProgressNotification(text))

    private fun showSuccessNotification(fileName: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_PROGRESS)
        nm.notify(NOTIF_ID_DONE,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("✓ Reel saved!")
                .setContentText(fileName)
                .setAutoCancel(true).setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW).build())
    }

    private fun showErrorNotification(error: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_PROGRESS)
        nm.notify(NOTIF_ID_DONE,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("ReelSaver — Download failed")
                .setContentText(error)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT).build())
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
