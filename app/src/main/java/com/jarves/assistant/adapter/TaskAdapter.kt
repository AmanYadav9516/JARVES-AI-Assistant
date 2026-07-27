package com.jarves.assistant.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jarves.assistant.R
import com.jarves.assistant.model.JarvesTask

class TaskAdapter(
    private var tasks: List<JarvesTask>,
    private val onDeleteClicked: (JarvesTask) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvIcon: TextView = itemView.findViewById(R.id.tvTaskIcon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        val tvSub: TextView = itemView.findViewById(R.id.tvTaskSub)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteTask)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.tvTitle.text = task.title

        val subText = if (task.delayMinutes > 0) {
            "Scheduled in ${task.delayMinutes} mins | Status: ${task.status}"
        } else {
            "Status: ${task.status}"
        }
        holder.tvSub.text = subText

        holder.tvIcon.text = when (task.actionType) {
            "CALL" -> "📞"
            "SMS" -> "✉️"
            "CAMERA", "PHOTO" -> "📷"
            "FLASHLIGHT" -> "🔦"
            "YOUTUBE" -> "🎵"
            "MAPS" -> "🗺️"
            "ALARM" -> "⏰"
            "REMINDER" -> "📅"
            "APP" -> "📱"
            else -> "⚡"
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClicked(task)
        }
    }

    override fun getItemCount(): Int = tasks.size

    fun updateTasks(newTasks: List<JarvesTask>) {
        this.tasks = newTasks
        notifyDataSetChanged()
    }
}
