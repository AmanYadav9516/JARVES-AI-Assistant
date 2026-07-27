package com.jarves.assistant.engine

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.widget.Toast
import com.jarves.assistant.model.JarvesTask
import java.util.Locale

class DeviceControlExecutor(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

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
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVES_TTS")
        }
        Toast.makeText(context, "JARVES: $text", Toast.LENGTH_SHORT).show()
    }

    fun execute(task: JarvesTask) {
        when (task.actionType) {
            "CALL" -> makeCall(task.target)
            "SMS" -> sendSms(task.target, task.detailText)
            "CAMERA" -> openCamera()
            "PHOTO" -> capturePhoto()
            "FLASHLIGHT" -> toggleFlashlight(task.target == "ON")
            "APP" -> openApp(task.target)
            "YOUTUBE" -> playYoutube(task.target)
            "MAPS" -> openMaps(task.target)
            "ALARM" -> setAlarm(task.detailText)
            "REMINDER" -> createReminder(task.detailText)
            "DELETE_TASK" -> deleteTask(task.target)
            "BATTERY" -> enableBatterySaver()
            else -> speak("Executing ${task.title}")
        }
    }

    private fun makeCall(contactNameOrNumber: String) {
        speak("Calling $contactNameOrNumber")
        try {
            val intent = if (contactNameOrNumber.matches(Regex("^[0-9+]+$"))) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$contactNameOrNumber"))
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            speak("Opening dialer")
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
        }
    }

    private fun sendSms(recipient: String, messageText: String) {
        speak("Sending message to $recipient")
        try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            if (recipient.matches(Regex("^[0-9+]+$"))) {
                smsManager.sendTextMessage(recipient, null, messageText, null, null)
                speak("Message sent successfully")
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

    private fun openApp(appNameOrPackage: String) {
        speak("Opening $appNameOrPackage")
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(appNameOrPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            // Search or fallback
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$appNameOrPackage")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                speak("App not found")
            }
        }
    }

    private fun playYoutube(query: String) {
        speak("Searching YouTube for $query")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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

    private fun setAlarm(hourStr: String) {
        val hour = hourStr.toIntOrNull() ?: 6
        speak("Setting alarm for $hour AM")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, 0)
            putExtra(AlarmClock.EXTRA_MESSAGE, "JARVES Alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun createReminder(reminderText: String) {
        speak("Reminder set: $reminderText")
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
        speak("Low Battery detected. Please turn on Battery Saver mode.")
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
