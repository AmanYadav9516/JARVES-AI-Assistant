package com.jarves.assistant.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.jarves.assistant.MainActivity
import com.jarves.assistant.R
import com.jarves.assistant.engine.DeviceControlExecutor
import com.jarves.assistant.engine.JarvesBrainEngine
import com.jarves.assistant.engine.TaskQueueManager
import com.jarves.assistant.model.JarvesTask
import com.jarves.assistant.receiver.CallAnnouncerReceiver
import com.jarves.assistant.receiver.PowerStateReceiver
import com.jarves.assistant.sensor.ShakeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class JarvesService : Service(), TaskQueueManager.QueueListener {

    private lateinit var executor: DeviceControlExecutor
    private lateinit var brainEngine: JarvesBrainEngine
    private var speechRecognizer: SpeechRecognizer? = null
    private var sensorManager: SensorManager? = null
    private var shakeDetector: ShakeDetector? = null

    private var powerStateReceiver: PowerStateReceiver? = null
    private var callAnnouncerReceiver: CallAnnouncerReceiver? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val handler = Handler(Looper.getMainLooper())

    private val CHANNEL_ID = "jarves_service_channel"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        executor = DeviceControlExecutor(this)
        brainEngine = JarvesBrainEngine()
        createNotificationChannel()
        TaskQueueManager.instance.addListener(this)

        setupBackgroundSpeechRecognizer()
        setupShakeSensor()
        registerReceivers()
    }

    private fun setupBackgroundSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        handler.post {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        // Continuous background listening loop restart
                        handler.postDelayed({ restartSpeechRecognizer() }, 1000)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0]
                            handleSpokenCommand(text)
                        }
                        handler.postDelayed({ restartSpeechRecognizer() }, 1000)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                restartSpeechRecognizer()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun restartSpeechRecognizer() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleSpokenCommand(commandText: String) {
        val lower = commandText.lowercase()
        if (lower.contains("hey jarves") || lower.contains("jarves") || lower.contains("जार्विस")) {
            // Show custom Dynamic Island Overlay directly
            val overlayIntent = Intent(this, JarvesOverlayService::class.java).apply {
                action = "ACTION_SHOW_PROCESSING"
                putExtra("EXTRA_TEXT", commandText)
            }
            try { startService(overlayIntent) } catch (e: Exception) {}

            serviceScope.launch {
                val tasks = brainEngine.parseVoiceCommand(commandText)
                if (tasks.isNotEmpty()) {
                    for (task in tasks) {
                        executor.execute(task)
                    }
                }
            }
        }
    }

    private fun setupShakeSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        shakeDetector = ShakeDetector {
            val intent = Intent(this, JarvesOverlayService::class.java).apply {
                action = "ACTION_SHOW_LISTENING"
                putExtra("EXTRA_TEXT", "Shake Triggered! Listening...")
            }
            try { startService(intent) } catch (e: Exception) { e.printStackTrace() }
        }

        if (accelerometer != null && shakeDetector != null) {
            sensorManager?.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun registerReceivers() {
        powerStateReceiver = PowerStateReceiver()
        val powerFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(powerStateReceiver, powerFilter)

        callAnnouncerReceiver = CallAnnouncerReceiver()
        val callFilter = IntentFilter("android.intent.action.PHONE_STATE")
        registerReceiver(callAnnouncerReceiver, callFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("JARVES Super-App Active", "Continuous Background Speech & Shake Active")
        startForeground(NOTIF_ID, notification)
        return START_STICKY
    }

    override fun onQueueUpdated(tasks: List<JarvesTask>) {
        val pendingCount = tasks.count { it.status == com.jarves.assistant.model.TaskStatus.PENDING }
        val activeTask = tasks.find { it.status == com.jarves.assistant.model.TaskStatus.RUNNING } ?: tasks.firstOrNull()

        val title = if (activeTask != null) "Active: ${activeTask.title}" else "JARVES System Active"
        val sub = "Pending Tasks: $pendingCount | Say 'HEY JARVES'"

        val notification = buildNotification(title, sub)
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(NOTIF_ID, notification)
    }

    override fun onTaskCompleted(task: JarvesTask) {
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val completionNotif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarves_logo)
            .setContentTitle("Task Completed ✅")
            .setContentText(task.title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notifManager.notify(System.currentTimeMillis().toInt(), completionNotif)
    }

    private fun buildNotification(title: String, content: String): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_jarves_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVES Assistant Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active JARVES voice assistant status, shake detector and background services"
            }
            val notifManager = getSystemService(NotificationManager::class.java)
            notifManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        speechRecognizer?.destroy()
        TaskQueueManager.instance.removeListener(this)
        if (sensorManager != null && shakeDetector != null) {
            sensorManager?.unregisterListener(shakeDetector)
        }
        if (powerStateReceiver != null) {
            try { unregisterReceiver(powerStateReceiver) } catch (e: Exception) {}
        }
        if (callAnnouncerReceiver != null) {
            try { unregisterReceiver(callAnnouncerReceiver) } catch (e: Exception) {}
        }
        executor.destroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
