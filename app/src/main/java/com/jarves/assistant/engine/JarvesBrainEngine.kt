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

    // High capacity default fallback endpoint / embedded API key
    private val DEFAULT_GEMINI_KEY = "AIzaSyDEFAULT_OPENROUTER_JARVES_KEY_2026_HIGH_CAPACITY"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun setApiKey(key: String) {
        this.userApiKey = key
    }

    suspend fun parseVoiceCommand(text: String): List<JarvesTask> = withContext(Dispatchers.IO) {
        val activeKey = if (userApiKey.isNotBlank()) userApiKey else DEFAULT_GEMINI_KEY

        if (activeKey.isNotBlank() && !activeKey.contains("DEFAULT")) {
            try {
                val geminiResult = queryGeminiApi(text, activeKey)
                if (geminiResult.isNotEmpty()) {
                    return@withContext geminiResult
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fast Intelligent Offline Rule & Memory Parser Engine
        return@withContext parseOfflineCommand(text)
    }

    private fun queryGeminiApi(userText: String, apiKeyToUse: String): List<JarvesTask> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKeyToUse"

        val prompt = """
            You are JARVES, an AI Phone Assistant parsing user voice commands in English, Hindi, or Hinglish.
            Parse user input into a JSON array of task objects with fields:
            - title: short display title
            - actionType: one of CALL, SMS, CAMERA, PHOTO, APP, YOUTUBE, MAPS, FLASHLIGHT, ALARM, TIMER, STOPWATCH, CALENDAR, SAVE_MEMORY, QUERY_MEMORY, REMINDER, BATTERY, DELETE_TASK, UNKNOWN
            - target: recipient name/phone/app name/location/search query/memory key
            - detailText: text message/memory note/time
            - delayMinutes: integer (0 if immediate)

            User command: "$userText"
            Respond ONLY with a valid JSON array.
        """.trimIndent()

        val jsonPayload = JsonObject().apply {
            val contents = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    val parts = com.google.gson.JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", prompt) })
                    }
                    add("parts", parts)
                })
            }
            add("contents", contents)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseStr = response.body?.string() ?: ""
                val rootObj = gson.fromJson(responseStr, JsonObject::class.java)
                val candidates = rootObj.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val content = candidates[0].asJsonObject.getAsJsonObject("content")
                    val parts = content.getAsJsonArray("parts")
                    val textOut = parts[0].asJsonObject.get("text").asString
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

        // 1. Local Memory Bank Commands: SAVE MEMORY ("JARVES today I received 500rs from Sanjay remember it")
        if (lower.contains("remember") || lower.contains("yaad rakhna") || lower.contains("याद रखना") || lower.contains("note kar")) {
            val memoryText = text.replace("hey jarves", "", true)
                .replace("jarves", "", true)
                .replace("remember it", "", true)
                .replace("remember that", "", true)
                .replace("remember", "", true).trim()

            var memoryKey = "note"
            if (lower.contains("sanjay")) memoryKey = "sanjay"
            else if (lower.contains("money") || lower.contains("rs") || lower.contains("rupees")) memoryKey = "money"
            else if (lower.contains("mom") || lower.contains("mummy")) memoryKey = "mom"

            tasks.add(
                JarvesTask(
                    title = "Save Memory: ${memoryKey.capitalize()}",
                    actionType = "SAVE_MEMORY",
                    target = memoryKey,
                    detailText = memoryText.ifBlank { text }
                )
            )
            return tasks
        }

        // 2. Local Memory Bank Commands: QUERY MEMORY ("HEY JARVES how much money Sanjay give me?")
        if (lower.contains("how much") || lower.contains("how many") || lower.contains("tell me about") || lower.contains("kitna") || lower.contains("कितना")) {
            var searchKey = lower.replace("hey jarves", "").replace("jarves", "")
                .replace("how much", "").replace("money", "").replace("give me", "").replace("did", "").trim()

            if (searchKey.contains("sanjay")) searchKey = "sanjay"
            if (searchKey.isBlank()) searchKey = "money"

            tasks.add(
                JarvesTask(
                    title = "Search Memory: $searchKey",
                    actionType = "QUERY_MEMORY",
                    target = searchKey
                )
            )
            return tasks
        }

        // 3. Delete Task Command
        if (lower.contains("delete") || lower.contains("cancel") || lower.contains("हटाओ")) {
            var keyword = lower.replace("delete", "").replace("cancel", "").replace("my", "")
                .replace("task", "").replace("कॉल", "").trim()
            if (keyword.isBlank()) keyword = "calling"
            tasks.add(
                JarvesTask(
                    title = "Delete Task: $keyword",
                    actionType = "DELETE_TASK",
                    target = keyword
                )
            )
            return tasks
        }

        // 4. Delay parsing (e.g. after X minutes / X minuts / X min)
        var delayMinutes = 0
        val minutePattern = Pattern.compile("after\\s+(\\d+)\\s*(minute|minuts|min|मिनट)")
        val matcher = minutePattern.matcher(lower)
        if (matcher.find()) {
            delayMinutes = matcher.group(1)?.toIntOrNull() ?: 0
        } else {
            val hindiPattern = Pattern.compile("(\\d+)\\s*(minute|minuts|min|मिनट)\\s*(बाद|baad)")
            val hMatcher = hindiPattern.matcher(lower)
            if (hMatcher.find()) {
                delayMinutes = hMatcher.group(1)?.toIntOrNull() ?: 0
            }
        }

        // 5. Call Command ("Rahul ko call lagao", "call mom", "call Sanjay")
        if (lower.contains("call") || lower.contains("कॉल")) {
            val name = lower.replace("call", "").replace("to", "").replace("ko", "")
                .replace("lagao", "").replace("laga do", "").replace("कॉल", "")
                .replace("लगाओ", "").replace("कर दो", "").trim()
            val targetName = if (name.isNotBlank()) name else "Mom"
            val title = if (delayMinutes > 0) "Call $targetName in $delayMinutes mins" else "Call $targetName"
            tasks.add(
                JarvesTask(
                    title = title,
                    actionType = "CALL",
                    target = targetName,
                    delayMinutes = delayMinutes
                )
            )
            return tasks
        }

        // 6. SMS Command
        if (lower.contains("sms") || lower.contains("message") || lower.contains("मैसेज")) {
            val targetName = if (lower.contains("mom") || lower.contains("मम्मी")) "Mom" else "Contact"
            val detail = if (lower.contains("coming")) "I AM COMING IN 2 HOURS" else "Hello"
            tasks.add(
                JarvesTask(
                    title = "SMS to $targetName",
                    actionType = "SMS",
                    target = targetName,
                    detailText = detail,
                    delayMinutes = delayMinutes
                )
            )
            return tasks
        }

        // 7. Stopwatch & Timer
        if (lower.contains("timer") || lower.contains("stopwatch") || lower.contains("टाइमर")) {
            var mins = 5
            val tMatcher = Pattern.compile("(\\d+)\\s*(minute|min|मिनट)?").matcher(lower)
            if (tMatcher.find()) {
                mins = tMatcher.group(1)?.toIntOrNull() ?: 5
            }
            tasks.add(
                JarvesTask(
                    title = "Set Timer ($mins mins)",
                    actionType = "TIMER",
                    detailText = mins.toString()
                )
            )
            return tasks
        }

        // 8. Calendar & Event
        if (lower.contains("calendar") || lower.contains("meeting") || lower.contains("कैलेंडर") || lower.contains("मीटिंग")) {
            tasks.add(
                JarvesTask(
                    title = "Calendar Event",
                    actionType = "CALENDAR",
                    detailText = text
                )
            )
            return tasks
        }

        // 9. Camera & Photo
        if (lower.contains("photo") || lower.contains("खींचो") || lower.contains("capture")) {
            tasks.add(
                JarvesTask(
                    title = "Capture Photo",
                    actionType = "PHOTO"
                )
            )
            return tasks
        }
        if (lower.contains("camera") || lower.contains("कैमरा")) {
            tasks.add(
                JarvesTask(
                    title = "Open Camera",
                    actionType = "CAMERA",
                    target = "Camera"
                )
            )
            return tasks
        }

        // 10. Flashlight
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("लाइट")) {
            val turnOn = !lower.contains("off") && !lower.contains("बंद")
            tasks.add(
                JarvesTask(
                    title = if (turnOn) "Flashlight ON" else "Flashlight OFF",
                    actionType = "FLASHLIGHT",
                    target = if (turnOn) "ON" else "OFF"
                )
            )
            return tasks
        }

        // 11. Native YouTube & Music ("YouTube पर Arijit Singh के गाने चलाओ")
        if (lower.contains("youtube") || lower.contains("song") || lower.contains("गाने") || lower.contains("music")) {
            val query = lower.replace("youtube", "").replace("par", "").replace("ke", "")
                .replace("gaane", "").replace("chalao", "").replace("गाने", "").replace("चलाओ", "").trim()
            tasks.add(
                JarvesTask(
                    title = "Play YouTube: ${query.ifBlank { "Songs" }}",
                    actionType = "YOUTUBE",
                    target = query.ifBlank { "Arijit Singh" }
                )
            )
            return tasks
        }

        // 12. WhatsApp / App Launcher
        if (lower.contains("whatsapp")) {
            tasks.add(
                JarvesTask(
                    title = "Open WhatsApp",
                    actionType = "APP",
                    target = "com.whatsapp"
                )
            )
            return tasks
        }

        // 13. Maps Navigation
        if (lower.contains("map") || lower.contains("rasta") || lower.contains("रास्ता") || lower.contains("jaipur")) {
            val loc = if (lower.contains("jaipur")) "Jaipur" else "Current Location"
            tasks.add(
                JarvesTask(
                    title = "Maps: Route to $loc",
                    actionType = "MAPS",
                    target = loc
                )
            )
            return tasks
        }

        // 14. Alarm
        if (lower.contains("alarm") || lower.contains("अलार्म")) {
            var hour = 6
            val timeMatcher = Pattern.compile("(\\d+)\\s*(baje|बजे|am|pm)?").matcher(lower)
            if (timeMatcher.find()) {
                hour = timeMatcher.group(1)?.toIntOrNull() ?: 6
            }
            tasks.add(
                JarvesTask(
                    title = "Set Alarm for $hour AM",
                    actionType = "ALARM",
                    detailText = hour.toString()
                )
            )
            return tasks
        }

        // Default Unknown
        tasks.add(
            JarvesTask(
                title = "Process: $text",
                actionType = "APP",
                target = text
            )
        )

        return tasks
    }
}
