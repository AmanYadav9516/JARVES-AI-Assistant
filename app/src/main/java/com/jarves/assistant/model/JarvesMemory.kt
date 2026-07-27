package com.jarves.assistant.model

data class JarvesMemory(
    val id: String = java.util.UUID.randomUUID().toString(),
    val key: String,
    val note: String,
    val timestamp: Long = System.currentTimeMillis()
)
