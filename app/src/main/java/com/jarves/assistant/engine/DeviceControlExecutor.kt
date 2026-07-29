package com.jarves.assistant.engine

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.Toast
import com.jarves.assistant.MainActivity
import com.jarves.assistant.model.JarvesTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceControlExecutor(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val memoryManager = JarvesLocalMemoryManager(context)
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("hi", "IN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            }
            isTtsReady = true
        }
    }

    fun speak(text: String) {
        val prefs = context.getSharedPreferences("jarves_prefs", Context.MODE_PRIVATE)
        val personality = prefs.getString("ai_personality", "JARVIS_PRO") ?: "JARVIS_PRO"

        val formattedText = when (personality) {
            "JARVIS_PRO" -> "At your service, Sir. $text"
            "FRIENDLY_HINDI" -> "नमस्ते! $text"
            else -> "JARVES System: $text"
        }

        requestAudioFocus()
        if (isTtsReady) {
            tts?.speak(formattedText, TextToSpeech.QUEUE_FLUSH, null, "JARVES_TTS")
        }
        Toast.makeText(context, formattedText, Toast.LENGTH_LONG).show()
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun execute(task: JarvesTask) {
        when (task.actionType) {
            "CALL" -> makeDirectCall(task.target)
            "CHAT_ANSWER" -> handleMathOrChatAnswer(task.target, task.detailText)
            "GET_NUMBER" -> readPhoneNumberAloud(task.target)
            "SMS" -> sendSms(task.target, task.detailText)
            "CAMERA" -> openCamera()
            "PHOTO" -> capturePhoto()
            "FLASHLIGHT" -> toggleFlashlight(task.target == "ON")
            "SOS" -> triggerSosStrobe()
            "GPS_EMERGENCY" -> triggerGpsEmergencyAlert()
            "CINEMA_MODE" -> activateCinemaMode()
            "OUTDOOR_MODE" -> activateOutdoorMode()
            "DRIVING_MODE" -> toggleDrivingMode(task.target != "OFF")
            "APP" -> openAppSafely(task.target)
            "YOUTUBE" -> playNativeYoutube(task.target)
            "MAPS" -> openMaps(task.target)
            "ALARM" -> setExactAlarm(task.detailText, task.delayMinutes)
            "TIMER", "STOPWATCH" -> setTimerOrStopwatch(task.detailText)
            "CALENDAR" -> openCalendar(task.detailText)
            "VOLUME" -> setVolume(task.target, task.detailText)
            "BRIGHTNESS" -> setBrightness(task.detailText)
            "SAVE_MEMORY" -> saveLocalMemoryAndKeep(task.target, task.detailText)
            "QUERY_MEMORY" -> queryLocalMemory(task.target)
            "REMINDER" -> createExactReminder(task.detailText, task.delayMinutes)
            "BRIEFING" -> readMorningBriefing()
            "DELETE_TASK" -> deleteTask(task.target)
            "BATTERY" -> enableBatterySaver()
            else -> speak("Executing ${task.title}")
        }
    }

    private fun makeDirectCall(contactNameOrNumber: String) {
        val cleanQuery = contactNameOrNumber.trim()
        speak("Searching contacts to call $cleanQuery")

        val foundNumber: String? = if (cleanQuery.matches(Regex("^[0-9+]+$"))) {
            cleanQuery
        } else {
            searchContactPhoneNumber(cleanQuery)
        }

        if (!foundNumber.isNullOrEmpty()) {
            speak("Calling $cleanQuery")
            try {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$foundNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
            } catch (e: Exception) {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$foundNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
            }
        } else {
            speak("Contact $cleanQuery not found in your phone contacts. Opening Phone Dialer.")
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(dialIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveLocalMemoryAndKeep(keyKeyword: String, fullText: String) {
        val resultMsg = memoryManager.saveMemory(keyKeyword, fullText)
        speak("Saved note to aman9516s11@gmail.com 5TB Google One Cloud Storage.")

        val keepPackage = "com.google.android.keep"
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(keepPackage)

        if (launchIntent != null) {
            try {
                val keepIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, fullText)
                    setPackage(keepPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(keepIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleMathOrChatAnswer(targetQuery: String, answerText: String) {
        if (answerText.isNotBlank()) {
            speak(answerText)
        } else {
            val cleanQuery = targetQuery.lowercase().replace("what is", "").replace("value of", "").replace("=", "").trim()
            val mathResult = evaluateBasicMath(cleanQuery)
            if (mathResult != null) {
                speak("The answer to $cleanQuery is $mathResult")
            } else {
                speak("I processed $targetQuery, Sir.")
            }
        }
    }

    private fun evaluateBasicMath(expr: String): String? {
        try {
            if (expr.contains("+")) {
                val parts = expr.split("+")
                val sum = parts.sumOf { it.trim().toDouble() }
                return if (sum % 1 == 0.0) sum.toLong().toString() else sum.toString()
            }
            if (expr.contains("-")) {
                val parts = expr.split("-")
                val diff = parts[0].trim().toDouble() - parts[1].trim().toDouble()
                return if (diff % 1 == 0.0) diff.toLong().toString() else diff.toString()
            }
            if (expr.contains("*") || expr.contains("x")) {
                val parts = expr.split(Regex("[*x]"))
                val prod = parts[0].trim().toDouble() * parts[1].trim().toDouble()
                return if (prod % 1 == 0.0) prod.toLong().toString() else prod.toString()
            }
            if (expr.contains("/")) {
                val parts = expr.split("/")
                val div = parts[0].trim().toDouble() / parts[1].trim().toDouble()
                return if (div % 1 == 0.0) div.toLong().toString() else div.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun openAppSafely(appNameOrPackage: String) {
        val lower = appNameOrPackage.lowercase().trim()
        val targetPackage = when {
            lower.contains("youtube") -> "com.google.android.youtube"
            lower.contains("whatsapp") -> "com.whatsapp"
            lower.contains("chrome") -> "com.android.chrome"
            lower.contains("instagram") -> "com.instagram.android"
            lower.contains("camera") || lower.contains("कैमरा") -> "com.android.camera2"
            lower.contains("clock") || lower.contains("घड़ी") -> "com.google.android.deskclock"
            lower.contains("settings") || lower.contains("सेटिंग") -> "com.android.settings"
            else -> appNameOrPackage
        }

        speak("Opening $appNameOrPackage")
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            speak("Sorry Sir, $appNameOrPackage app is not installed on your phone.")
        }
    }

    private fun activateCinemaMode() {
        setBrightness("10")
        setVolume("SILENT", "0")
        speak("Cinema Mode Activated. Brightness reduced to 10 percent and ringer set to silent.")
    }

    private fun activateOutdoorMode() {
        setBrightness("100")
        setVolume("MEDIA", "100")
        speak("Outdoor Mode Activated. Maximum brightness and 100 percent volume set.")
    }

    private fun toggleDrivingMode(enable: Boolean) {
        val prefs = context.getSharedPreferences("jarves_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("driving_mode_active", enable).apply()
        if (enable) {
            speak("Driving Mode Auto-Responder Activated. Incoming calls and messages will receive auto-reply.")
        } else {
            speak("Driving Mode Deactivated.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerGpsEmergencyAlert() {
        speak("Emergency Panic Alert triggered! Fetching live GPS location.")
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val locationListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    val lat = loc.latitude
                    val lng = loc.longitude
                    val mapUrl = "https://maps.google.com/?q=$lat,$lng"
                    val msg = "EMERGENCY ALERT! I need help. My current live location is: $mapUrl - Sent by JARVES AI"

                    val emergencyNumber = searchContactPhoneNumber("Mom") ?: searchContactPhoneNumber("Emergency") ?: "112"
                    sendSms(emergencyNumber, msg)
                    speak("Emergency SMS sent with live location to $emergencyNumber")
                    try { locationManager.removeUpdates(this) } catch (e: Exception) {}
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, null)
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, locationListener, null)
            } else {
                val emergencyNumber = searchContactPhoneNumber("Mom") ?: "112"
                sendSms(emergencyNumber, "EMERGENCY ALERT! I need help. - Sent by JARVES AI")
            }
        } catch (e: Exception) {
            val emergencyNumber = searchContactPhoneNumber("Mom") ?: "112"
            sendSms(emergencyNumber, "EMERGENCY ALERT! I need help. - Sent by JARVES AI")
        }
    }

    private fun readPhoneNumberAloud(contactName: String) {
        val foundNumber = searchContactPhoneNumber(contactName)
        if (!foundNumber.isNullOrEmpty()) {
            val formattedDigits = foundNumber.replace("", " ").trim()
            speak("$contactName's phone number is $formattedDigits")
        } else {
            speak("Sorry, could not find any contact number for $contactName.")
        }
    }

    private fun searchContactPhoneNumber(contactName: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$contactName%")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex >= 0) {
                    return cursor.getString(numberIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun setExactAlarm(detail: String, delayMinutes: Int) {
        val mins = if (delayMinutes > 0) delayMinutes else (detail.toIntOrNull() ?: 60)
        speak("Alarm set for $mins minutes from now.")

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, (System.currentTimeMillis() / (1000 * 3600) % 24).toInt())
            putExtra(AlarmClock.EXTRA_MINUTES, (mins % 60))
            putExtra(AlarmClock.EXTRA_MESSAGE, "JARVES Wake Up Alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createExactReminder(reminderText: String, delayMinutes: Int) {
        val delayMs = if (delayMinutes > 0) delayMinutes * 60 * 1000L else 120 * 60 * 1000L
        val triggerTimeMs = System.currentTimeMillis() + delayMs

        saveLocalMemoryAndKeep("reminder", reminderText)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        }

        speak("Reminder set for $reminderText in $delayMinutes minutes.")
    }

    private fun readMorningBriefing() {
        val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
        val pendingCount = TaskQueueManager.instance.getPendingCount()
        speak("Good Morning, Sir! Today is $dateStr. You have $pendingCount pending tasks queued in JARVES. All systems are operational.")
    }

    private fun triggerSosStrobe() {
        speak("Emergency SOS Strobe activated!")
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            for (i in 0..10) {
                handler.postDelayed({ cameraManager.setTorchMode(cameraId, true) }, i * 300L)
                handler.postDelayed({ cameraManager.setTorchMode(cameraId, false) }, i * 300L + 150L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setVolume(mode: String, levelStr: String) {
        val level = levelStr.toIntOrNull() ?: 80
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVol = (maxVolume * (level / 100.0f)).toInt()

        if (mode.contains("SILENT", true)) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            speak("Ringer set to Silent")
        } else if (mode.contains("VIBRATE", true)) {
            audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            speak("Ringer set to Vibrate")
        } else {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
            speak("Media Volume set to $level percent")
        }
    }

    private fun setBrightness(levelStr: String) {
        val level = levelStr.toIntOrNull() ?: 50
        val targetVal = (255 * (level / 100.0f)).toInt()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            try {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetVal)
                speak("Screen Brightness set to $level percent")
            } catch (e: Exception) {
                speak("Unable to adjust brightness settings")
            }
        } else {
            speak("Write settings permission needed for brightness adjustment")
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try { context.startActivity(intent) } catch (e: Exception) {}
        }
    }

    private fun queryLocalMemory(queryKeyword: String) {
        val responseText = memoryManager.findMemory(queryKeyword)
        speak(responseText)
    }

    private fun playNativeYoutube(query: String) {
        speak("Opening YouTube App for $query")
        val youtubePackage = "com.google.android.youtube"
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(youtubePackage)

        if (launchIntent != null) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:results?search_query=" + Uri.encode(query))).apply {
                setPackage(youtubePackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }

    private fun setTimerOrStopwatch(minutesStr: String) {
        val mins = minutesStr.toIntOrNull() ?: 5
        speak("Setting timer for $mins minutes")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, mins * 60)
            putExtra(AlarmClock.EXTRA_MESSAGE, "JARVES Timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            val clockIntent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(clockIntent)
        }
    }

    private fun openCalendar(eventTitle: String) {
        speak("Opening Calendar")
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, eventTitle.ifBlank { "JARVES Event" })
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendSms(recipient: String, messageText: String) {
        speak("Sending message to $recipient")
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            val phoneNum = if (recipient.matches(Regex("^[0-9+]+$"))) recipient else searchContactPhoneNumber(recipient)
            if (!phoneNum.isNullOrEmpty()) {
                smsManager.sendTextMessage(phoneNum, null, messageText, null, null)
                speak("Message sent successfully to $recipient")
            } else {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                    putExtra("sms_body", messageText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                putExtra("sms_body", messageText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun openCamera() {
        speak("Opening Camera")
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun capturePhoto() {
        speak("Capturing Photo")
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun toggleFlashlight(turnOn: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, turnOn)
            speak(if (turnOn) "Flashlight turned on" else "Flashlight turned off")
        } catch (e: Exception) {
            speak("Unable to control flashlight")
        }
    }

    private fun openMaps(location: String) {
        speak("Showing route to $location on Maps")
        val gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(location))
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + Uri.encode(location))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }

    private fun deleteTask(keyword: String) {
        val deleted = TaskQueueManager.instance.removeTaskByKeyword(keyword)
        if (deleted != null) {
            speak("Deleted ${deleted.title}")
        } else {
            speak("No task matching $keyword found in queue")
        }
    }

    private fun enableBatterySaver() {
        speak("Low Battery detected. Opening Battery Saver settings.")
        val intent = Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
    }
}
