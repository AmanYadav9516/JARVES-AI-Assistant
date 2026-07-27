package com.jarves.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jarves.assistant.MainActivity
import com.jarves.assistant.R
import com.jarves.assistant.engine.DeviceControlExecutor
import com.jarves.assistant.engine.TaskQueueManager
import com.jarves.assistant.model.JarvesTask

class JarvesService : Service(), TaskQueueManager.QueueListener {

    private lateinit var executor: DeviceControlExecutor
    private val CHANNEL_ID = "jarves_service_channel"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        executor = DeviceControlExecutor(this)
        createNotificationChannel()
        TaskQueueManager.instance.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("JARVES System Active", "Listening for voice & scheduled tasks...")
        startForeground(NOTIF_ID, notification)
        return START_STICKY
    }

    override fun onQueueUpdated(tasks: List<JarvesTask>) {
        val pendingCount = tasks.count { it.status == com.jarves.assistant.model.TaskStatus.PENDING }
        val activeTask = tasks.find { it.status == com.jarves.assistant.model.TaskStatus.RUNNING }
            ?: tasks.firstOrNull()

        val title = if (activeTask != null) "Active: ${activeTask.title}" else "JARVES Assistant Active"
        val sub = "Pending Tasks: $pendingCount | Tap to open JARVES"

        val notification = buildNotification(title, sub)
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(NOTIF_ID, notification)
    }

    override fun onTaskCompleted(task: JarvesTask) {
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val completionNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarves_logo)
            .setContentTitle("Task Completed ✅")
            .setContentText(task.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notifManager.notify(System.currentTimeMillis().toInt(), completionNotif)
    }

    private fun buildNotification(title: String, content: String): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_jarves_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVES Assistant Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active JARVES voice assistant status and pending task queue"
            }
            val notifManager = getSystemService(NotificationManager::class.java)
            notifManager?.createNotificationChannel(channel)
        }
    }

    fun getExecutor(): DeviceControlExecutor = executor

    override fun onDestroy() {
        super.onDestroy()
        TaskQueueManager.instance.removeListener(this)
        executor.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
