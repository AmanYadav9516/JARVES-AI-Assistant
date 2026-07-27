package com.jarves.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarves.assistant.engine.TaskQueueManager

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val taskId = intent?.getStringExtra("EXTRA_TASK_ID")
        if (!taskId.isNullOrEmpty()) {
            TaskQueueManager.instance.removeTaskById(taskId)
        }
    }
}
