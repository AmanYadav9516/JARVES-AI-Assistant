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
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun setApiKey(key: String) {
        if (key.isNotBlank()) {
            this.userApiKey = key
        }
    }

    suspend fun parseVoiceCommand(text: String): List<JarvesTask> = withContext(Dispatchers.IO) {
        // Fast instant local offline parsing first to eliminate lagging
        val offlineTasks = parseOfflineCommand(text)
        if (offlineTasks.isNotEmpty() && offlineTasks.first().actionType != "APP" && offlineTasks.first().actionType != "UNKNOWN") {
            return@withContext offlineTasks
        }

        // High speed OpenRouter AI comprehension for complex requests
        val activeKey = if (userApiKey.isNotBlank()) userApiKey else OPENROUTER_API_KEY
        try {
            val openRouterTasks = queryOpenRouterApi(text, activeKey)
            if (openRouterTasks.isNotEmpty()) {
                return@withContext openRouterTasks
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext offlineTasks
    }

    private fun queryOpenRouterApi(userText: String, apiKeyToUse: String): List<JarvesTask> {
        val url = "https://openrouter.ai/api/v1/chat/completions"

        val systemPrompt = """
            You are JARVES, a Super AI Phone Assistant parsing user voice commands in English, Hindi, or Hinglish.
            Parse user input into a JSON array of task objects with fields:
            - title: short display title
            - actionType: one of CALL, GET_NUMBER, SMS, CAMERA, PHOTO, APP, YOUTUBE, MAPS, FLASHLIGHT, SOS, GPS_EMERGENCY, CINEMA_MODE, OUTDOOR_MODE, DRIVING_MODE, ALARM, TIMER, STOPWATCH, CALENDAR, VOLUME, BRIGHTNESS, SAVE_MEMORY, QUERY_MEMORY, REMINDER, BRIEFING, BATTERY, DELETE_TASK, UNKNOWN
            - target: recipient name/phone/app name/location/search query/memory key/volume mode
            - detailText: text message/memory note/time/percentage
            - delayMinutes: integer (calculate duration in minutes if user specifies 2 hours, 180 minutes, etc., 0 if immediate)

            User command: "$userText"
            Respond ONLY with a valid JSON array.
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

        // 1. Cinema Mode & Outdoor Mode & Driving Mode
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

        // 2. Morning Briefing
        if (lower.contains("good morning") || lower.contains("briefing")) {
            tasks.add(JarvesTask(title = "Morning Briefing", actionType = "BRIEFING"))
            return tasks
        }

        // 3. Emergency SOS & GPS Panic
        if (lower.contains("emergency") || lower.contains("sos") || lower.contains("panic")) {
            tasks.add(JarvesTask(title = "Emergency SOS & Live GPS", actionType = "GPS_EMERGENCY"))
            return tasks
        }

        // 4. Get Phone Number Read Aloud
        if ((lower.contains("number") || lower.contains("नंबर")) && (lower.contains("kya") || lower.contains("what") || lower.contains("batao"))) {
            var contact = lower.replace("hey jarves", "").replace("jarves", "").replace("ka", "").replace("ki", "")
                .replace("phone", "").replace("number", "").replace("kya", "").replace("hai", "").replace("batao", "").replace("what", "").replace("is", "").trim()
            if (contact.isBlank()) contact = "Mom"
            tasks.add(JarvesTask(title = "Get Number: $contact", actionType = "GET_NUMBER", target = contact))
            return tasks
        }

        // 5. Extended Duration Parsing for Alarms & Reminders
        var delayMinutes = 0
        val hourPattern = Pattern.compile("(\\d+)\\s*(ghante|ghanta|hour|hours|घंटे)")
        val hMatcher = hourPattern.matcher(lower)
        if (hMatcher.find()) {
            val hours = hMatcher.group(1)?.toIntOrNull() ?: 2
            delayMinutes = hours * 60
        } else {
            val minutePattern = Pattern.compile("(\\d+)\\s*(minute|minuts|min|मिनट)")
            val mMatcher = minutePattern.matcher(lower)
            if (mMatcher.find()) {
                delayMinutes = mMatcher.group(1)?.toIntOrNull() ?: 0
            }
        }

        if (lower.contains("jga") || lower.contains("जगा") || lower.contains("reminder") || lower.contains("याद dila")) {
            val reminderTitle = text.ifBlank { "Wake Up & Reminder" }
            tasks.add(
                JarvesTask(
                    title = "Reminder ($delayMinutes mins)",
                    actionType = "REMINDER",
                    detailText = reminderTitle,
                    delayMinutes = delayMinutes
                )
            )
            return tasks
        }

        // 6. Volume Control
        if (lower.contains("volume") || lower.contains("sound") || lower.contains("आवाज़") || lower.contains("silent") || lower.contains("vibrate")) {
            var percent = 80
            val pMatcher = Pattern.compile("(\\d+)").matcher(lower)
            if (pMatcher.find()) {
                percent = pMatcher.group(1)?.toIntOrNull() ?: 80
            }
            val mode = if (lower.contains("silent")) "SILENT" else if (lower.contains("vibrate")) "VIBRATE" else "MEDIA"
            tasks.add(
                JarvesTask(
                    title = "Set Volume ($percent%)",
                    actionType = "VOLUME",
                    target = mode,
                    detailText = percent.toString()
                )
            )
            return tasks
        }

        // 7. Brightness Control
        if (lower.contains("brightness") || lower.contains("लाइट") || lower.contains("रोशनी")) {
            var percent = 50
            val bMatcher = Pattern.compile("(\\d+)").matcher(lower)
            if (bMatcher.find()) {
                percent = bMatcher.group(1)?.toIntOrNull() ?: 50
            }
            tasks.add(
                JarvesTask(
                    title = "Set Brightness ($percent%)",
                    actionType = "BRIGHTNESS",
                    detailText = percent.toString()
                )
            )
            return tasks
        }

        // 8. Local Memory Bank Commands
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
                    title = "Save Memory: ${memoryKey.replaceFirstChar { it.uppercase() }}",
                    actionType = "SAVE_MEMORY",
                    target = memoryKey,
                    detailText = memoryText.ifBlank { text }
                )
            )
            return tasks
        }

        // 9. Query Memory
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

        // 10. Delete Task Command
        if (lower.contains("delete") || lower.contains("cancel") || lower.contains("हटाओ")) {
            var keyword = lower.replace("delete", "").replace("cancel", "").replace("my", "")
                .replace("task", "").replace("कॉल", "").trim()
            if (keyword.isBlank()) keyword = "calling"
            tasks.add(JarvesTask(title = "Delete Task: $keyword", actionType = "DELETE_TASK", target = keyword))
            return tasks
        }

        // 11. Call Command
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

        // 12. SMS Command
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

        // 13. Stopwatch & Timer
        if (lower.contains("timer") || lower.contains("stopwatch") || lower.contains("टाइमर")) {
            var mins = 5
            val tMatcher = Pattern.compile("(\\d+)\\s*(minute|min|मिनट)?").matcher(lower)
            if (tMatcher.find()) {
                mins = tMatcher.group(1)?.toIntOrNull() ?: 5
            }
            tasks.add(JarvesTask(title = "Set Timer ($mins mins)", actionType = "TIMER", detailText = mins.toString()))
            return tasks
        }

        // 14. Camera & Photo
        if (lower.contains("photo") || lower.contains("खींचो") || lower.contains("capture")) {
            tasks.add(JarvesTask(title = "Capture Photo", actionType = "PHOTO"))
            return tasks
        }
        if (lower.contains("camera") || lower.contains("कैमरा")) {
            tasks.add(JarvesTask(title = "Open Camera", actionType = "CAMERA", target = "Camera"))
            return tasks
        }

        // 15. Flashlight
        if (lower.contains("flashlight") || lower.contains("torch") || lower.contains("लाइट")) {
            val turnOn = !lower.contains("off") && !lower.contains("बंद")
            tasks.add(JarvesTask(title = if (turnOn) "Flashlight ON" else "Flashlight OFF", actionType = "FLASHLIGHT", target = if (turnOn) "ON" else "OFF"))
            return tasks
        }

        // 16. Native YouTube & Music
        if (lower.contains("youtube") || lower.contains("song") || lower.contains("गाने") || lower.contains("music")) {
            val query = lower.replace("youtube", "").replace("par", "").replace("ke", "")
                .replace("gaane", "").replace("chalao", "").replace("गाने", "").replace("चलाओ", "").trim()
            tasks.add(JarvesTask(title = "Play YouTube: ${query.ifBlank { "Songs" }}", actionType = "YOUTUBE", target = query.ifBlank { "Arijit Singh" }))
            return tasks
        }

        // 17. WhatsApp / App Launcher
        if (lower.contains("whatsapp")) {
            tasks.add(JarvesTask(title = "Open WhatsApp", actionType = "APP", target = "com.whatsapp"))
            return tasks
        }

        // 18. Maps Navigation
        if (lower.contains("map") || lower.contains("rasta") || lower.contains("रास्ता") || lower.contains("jaipur")) {
            val loc = if (lower.contains("jaipur")) "Jaipur" else "Current Location"
            tasks.add(JarvesTask(title = "Maps: Route to $loc", actionType = "MAPS", target = loc))
            return tasks
        }

        // 19. Alarm
        if (lower.contains("alarm") || lower.contains("अलार्म")) {
            var hour = 6
            val timeMatcher = Pattern.compile("(\\d+)\\s*(baje|बजे|am|pm)?").matcher(lower)
            if (timeMatcher.find()) {
                hour = timeMatcher.group(1)?.toIntOrNull() ?: 6
            }
            tasks.add(JarvesTask(title = "Set Alarm for $hour AM", actionType = "ALARM", detailText = hour.toString()))
            return tasks
        }

        // Default Unknown
        tasks.add(JarvesTask(title = "Process: $text", actionType = "APP", target = text))
        return tasks
    }
}
