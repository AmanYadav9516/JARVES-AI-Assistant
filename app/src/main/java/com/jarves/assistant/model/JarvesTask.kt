package com.jarves.assistant.model

data class JarvesTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val actionType: String, // CALL, SMS, CAMERA, PHOTO, APP, YOUTUBE, MAPS, FLASHLIGHT, ALARM, REMINDER, BATTERY, DELETE_TASK
    val target: String = "",
    val detailText: String = "",
    val delayMinutes: Int = 0,
    val scheduledTimeMillis: Long = System.currentTimeMillis() + (delayMinutes * 60 * 1000L),
    var status: TaskStatus = TaskStatus.PENDING
)

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    CANCELLED
}
