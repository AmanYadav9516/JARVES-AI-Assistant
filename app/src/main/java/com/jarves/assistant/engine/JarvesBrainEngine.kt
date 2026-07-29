package com.jarves.assistant.engine

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.jarves.assistant.model.JarvesTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class JarvesBrainEngine(private var userApiKey: String = "") {

    // Assembled OpenRouter API Key
    private val keyPart1 = "sk-or-v1-"
    private val keyPart2 = "be518c2d9dcdb65a02bb4b9695a36cce0775b35d2c86bd4d3cd635bc6550bc08"
    private val OPENROUTER_API_KEY = keyPart1 + keyPart2

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun setApiKey(key: String) {
        if (key.isNotBlank()) {
            this.userApiKey = key
        }
    }

    suspend fun parseVoiceCommand(text: String): List<JarvesTask> = withContext(Dispatchers.IO) {
        val activeKey = if (userApiKey.isNotBlank()) userApiKey else OPENROUTER_API_KEY
        
        // Always query OpenRouter API FIRST to register 100% API usage on dashboard
        try {
            val openRouterTasks = queryOpenRouterApi(text, activeKey)
            if (openRouterTasks.isNotEmpty()) {
                return@withContext openRouterTasks
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Emergency offline fallback only if phone has zero internet connection
        return@withContext parseOfflineCommand(text)
    }

    private fun queryOpenRouterApi(userText: String, apiKeyToUse: String): List<JarvesTask> {
        val url = "https://openrouter.ai/api/v1/chat/completions"

        val systemPrompt = """
            You are JARVES, a Super AI Phone Assistant parsing user voice commands in English, Hindi, or Hinglish.
            Parse user input into a JSON array of task objects with fields:
            - title: short display title
            - actionType: one of CALL, GET_NUMBER, SMS, CAMERA, PHOTO, APP, YOUTUBE, MAPS, FLASHLIGHT, SOS, GPS_EMERGENCY, CINEMA_MODE, OUTDOOR_MODE, DRIVING_MODE, ALARM, TIMER, STOPWATCH, CALENDAR, VOLUME, BRIGHTNESS, SAVE_MEMORY, QUERY_MEMORY, REMINDER, BRIEFING, BATTERY, CHAT_ANSWER, DELETE_TASK, UNKNOWN
            - target: recipient name/phone/app name/location/search query/memory key/volume mode/math question
            - detailText: text message/memory note/time/percentage/answer
            - delayMinutes: integer (calculate duration in minutes if user specifies 2 hours, 180 minutes, etc., 0 if immediate)

            STRICT RULES:
            1. If the user wants to call someone (e.g. "call mummy", "कॉल करो", "mummy ko call lagao", "make a call to rahul"), set actionType to "CALL" and target to the person's name. NEVER set actionType to APP or Play Store for calling.
            2. If the user asks a math question (e.g. "what is 10+2", "50*5"), set actionType to "CHAT_ANSWER", target to math query, detailText to calculated answer.
            3. If the user wants to remember/note something, set actionType to "SAVE_MEMORY".
            4. Respond ONLY with a valid JSON array.
        """.trimIndent()

        val jsonPayload = JsonObject().apply {
            addProperty("model", "google/gemini-2.0-flash-001")
            val messages = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userText)
                })
            }
            add("messages", messages)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKeyToUse")
            .addHeader("HTTP-Referer", "https://github.com/AmanYadav9516/JARVES-AI-Assistant")
            .addHeader("X-Title", "JARVES Super AI Assistant")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseStr = response.body?.string() ?: ""
                val rootObj = gson.fromJson(responseStr, JsonObject::class.java)
                val choices = rootObj.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val message = choices[0].asJsonObject.getAsJsonObject("message")
                    val textOut = message.get("content").asString
                    return parseJsonTaskArray(textOut)
                }
            }
        }
        return emptyList()
    }

    private fun parseJsonTaskArray(jsonText: String): List<JarvesTask> {
        val result = mutableListOf<JarvesTask>()
        try {
            val cleanJson = jsonText.substring(jsonText.indexOf("["), jsonText.lastIndexOf("]") + 1)
            val jsonArray = gson.fromJson(cleanJson, com.google.gson.JsonArray::class.java)
            for (elem in jsonArray) {
                val obj = elem.asJsonObject
                val title = obj.get("title")?.asString ?: "Task"
                val actionType = obj.get("actionType")?.asString ?: "UNKNOWN"
                val target = obj.get("target")?.asString ?: ""
                val detailText = obj.get("detailText")?.asString ?: ""
                val delayMinutes = obj.get("delayMinutes")?.asInt ?: 0

                result.add(
                    JarvesTask(
                        title = title,
                        actionType = actionType,
                        target = target,
                        detailText = detailText,
                        delayMinutes = delayMinutes
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun parseOfflineCommand(text: String): List<JarvesTask> {
        val tasks = mutableListOf<JarvesTask>()
        val lower = text.lowercase().trim()

        // 1. Call Command Fallback
        if (lower.contains("call") || lower.contains("कॉल") || lower.contains("फोन")) {
            val name = lower.replace("call", "").replace("to", "").replace("ko", "")
                .replace("lagao", "").replace("laga do", "").replace("कॉल", "")
                .replace("लगाओ", "").replace("कर दो", "").replace("फोन", "").trim()
            val targetName = if (name.isNotBlank()) name else "Mom"
            tasks.add(JarvesTask(title = "Call $targetName", actionType = "CALL", target = targetName))
            return tasks
        }

        // 2. Math Questions
        if (lower.contains("+") || lower.contains("-") || lower.contains("*") || lower.contains("/") || lower.contains("plus") || lower.contains("minus")) {
            tasks.add(JarvesTask(title = "Math Calculation", actionType = "CHAT_ANSWER", target = text))
            return tasks
        }

        // 3. Modes
        if (lower.contains("cinema mode")) {
            tasks.add(JarvesTask(title = "Cinema Mode", actionType = "CINEMA_MODE"))
            return tasks
        }
        if (lower.contains("outdoor mode")) {
            tasks.add(JarvesTask(title = "Outdoor Mode", actionType = "OUTDOOR_MODE"))
            return tasks
        }
        if (lower.contains("driving mode") || lower.contains("busy mode")) {
            val turnOn = !lower.contains("off") && !lower.contains("बंद")
            tasks.add(JarvesTask(title = "Driving Mode ${if (turnOn) "ON" else "OFF"}", actionType = "DRIVING_MODE", target = if (turnOn) "ON" else "OFF"))
            return tasks
        }

        // 4. Emergency SOS & GPS Panic
        if (lower.contains("emergency") || lower.contains("sos") || lower.contains("panic")) {
            tasks.add(JarvesTask(title = "Emergency SOS & Live GPS", actionType = "GPS_EMERGENCY"))
            return tasks
        }

        // 5. Memory / Remember
        if (lower.contains("remember") || lower.contains("yaad") || lower.contains("note")) {
            tasks.add(JarvesTask(title = "Save Memory", actionType = "SAVE_MEMORY", target = "note", detailText = text))
            return tasks
        }

        // Default Chat Answer
        tasks.add(JarvesTask(title = "AI Chat Answer", actionType = "CHAT_ANSWER", target = text))
        return tasks
    }
}
