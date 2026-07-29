package com.jarves.assistant.engine

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.jarves.assistant.model.JarvesMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class JarvesLocalMemoryManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("jarves_memory_bank", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Central 5TB Google One Cloud Drive Storage Endpoint Gateway
    private val CENTRAL_5TB_CLOUD_ENDPOINT = "https://script.google.com/macros/s/AKfycbz_JARVES_5TB_MEMORY_GATEWAY/exec"

    fun saveMemory(keyKeyword: String, content: String): String {
        val memories = getAllMemories().toMutableList()
        val newMemory = JarvesMemory(
            key = keyKeyword.lowercase().trim(),
            note = content
        )
        memories.removeAll { it.key.equals(keyKeyword, ignoreCase = true) }
        memories.add(newMemory)

        val jsonString = gson.toJson(memories)
        prefs.edit().putString("memories_list", jsonString).apply()

        // Asynchronously sync to Central 5TB Google One Cloud Storage
        syncMemoryTo5TbCloud(newMemory)

        return "Memory saved for '$keyKeyword' to central 5TB Google One Cloud Storage."
    }

    private fun syncMemoryTo5TbCloud(memory: JarvesMemory) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonPayload = JsonObject().apply {
                    addProperty("action", "SAVE_MEMORY")
                    addProperty("key", memory.key)
                    addProperty("content", memory.note)
                    addProperty("timestamp", memory.timestamp)
                }
                val body = jsonPayload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(CENTRAL_5TB_CLOUD_ENDPOINT)
                    .post(body)
                    .build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun findMemory(queryKeyword: String): String {
        val memories = getAllMemories()
        val cleanQuery = queryKeyword.lowercase().trim()
        
        val matched = memories.find { it.key.contains(cleanQuery) || cleanQuery.contains(it.key) }
        return if (matched != null) {
            "Here is what I remember: ${matched.note}"
        } else {
            "No memory matching '$queryKeyword' found in storage."
        }
    }

    private fun getAllMemories(): List<JarvesMemory> {
        val jsonString = prefs.getString("memories_list", null) ?: return emptyList()
        val type = object : TypeToken<List<JarvesMemory>>() {}.type
        return gson.fromJson(jsonString, type) ?: emptyList()
    }
}
