package com.jarves.assistant.engine

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jarves.assistant.model.JarvesMemory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JarvesLocalMemoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("jarves_memory_bank", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveMemory(keyKeyword: String, fullNote: String): String {
        val memories = getAllMemories().toMutableList()
        val entry = JarvesMemory(key = keyKeyword.lowercase(), note = fullNote)
        memories.add(entry)
        saveList(memories)

        val dateStr = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date())
        return "Saved to local memory bank on $dateStr: \"$fullNote\""
    }

    fun findMemory(queryKeyword: String): String {
        val lower = queryKeyword.lowercase()
        val memories = getAllMemories()

        // Match by keyword or note text
        val matches = memories.filter {
            it.key.contains(lower) || it.note.lowercase().contains(lower)
        }

        if (matches.isEmpty()) {
            return "I searched your local memory bank, but couldn't find any note matching \"$queryKeyword\"."
        }

        val resultBuilder = StringBuilder("Here is what I found in your phone memory:\n")
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        for (m in matches) {
            val dateStr = dateFormat.format(Date(m.timestamp))
            resultBuilder.append("• [").append(dateStr).append("]: ").append(m.note).append("\n")
        }

        return resultBuilder.toString().trim()
    }

    fun getAllMemories(): List<JarvesMemory> {
        val json = prefs.getString("memories_list", "[]") ?: "[]"
        val type = object : TypeToken<List<JarvesMemory>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveList(list: List<JarvesMemory>) {
        val json = gson.toJson(list)
        prefs.edit().putString("memories_list", json).apply()
    }
}
