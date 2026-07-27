package com.jarves.assistant.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.jarves.assistant.model.JarvesTask
import com.jarves.assistant.model.TaskStatus
import java.util.concurrent.CopyOnWriteArrayList

class TaskQueueManager private constructor() {

    interface QueueListener {
        fun onQueueUpdated(tasks: List<JarvesTask>)
        fun onTaskCompleted(task: JarvesTask)
    }

    private val tasks = CopyOnWriteArrayList<JarvesTask>()
    private val listeners = CopyOnWriteArrayList<QueueListener>()
    private val handler = Handler(Looper.getMainLooper())

    fun addListener(listener: QueueListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
        listener.onQueueUpdated(getTasks())
    }

    fun removeListener(listener: QueueListener) {
        listeners.remove(listener)
    }

    fun getTasks(): List<JarvesTask> = tasks.toList()

    fun getPendingCount(): Int = tasks.count { it.status == TaskStatus.PENDING }

    fun addTask(context: Context, task: JarvesTask, executor: (JarvesTask) -> Unit) {
        tasks.add(task)
        notifyUpdated()

        if (task.delayMinutes > 0) {
            val delayMillis = task.delayMinutes * 60 * 1000L
            handler.postDelayed({
                executeTask(task, executor)
            }, delayMillis)
        } else {
            executeTask(task, executor)
        }
    }

    private fun executeTask(task: JarvesTask, executor: (JarvesTask) -> Unit) {
        if (task.status == TaskStatus.CANCELLED || !tasks.contains(task)) return

        task.status = TaskStatus.RUNNING
        notifyUpdated()

        executor(task)

        task.status = TaskStatus.COMPLETED
        tasks.remove(task)
        notifyUpdated()

        for (l in listeners) {
            l.onTaskCompleted(task)
        }
    }

    fun removeTaskById(taskId: String): Boolean {
        val task = tasks.find { it.id == taskId }
        return if (task != null) {
            task.status = TaskStatus.CANCELLED
            tasks.remove(task)
            notifyUpdated()
            true
        } else {
            false
        }
    }

    fun removeTaskByKeyword(keyword: String): JarvesTask? {
        val lower = keyword.lowercase()
        val targetTask = tasks.find {
            it.title.lowercase().contains(lower) ||
                    it.actionType.lowercase().contains(lower) ||
                    it.target.lowercase().contains(lower)
        }
        if (targetTask != null) {
            removeTaskById(targetTask.id)
        }
        return targetTask
    }

    private fun notifyUpdated() {
        val currentList = getTasks()
        for (l in listeners) {
            l.onQueueUpdated(currentList)
        }
    }

    companion object {
        val instance: TaskQueueManager by lazy { TaskQueueManager() }
    }
}
