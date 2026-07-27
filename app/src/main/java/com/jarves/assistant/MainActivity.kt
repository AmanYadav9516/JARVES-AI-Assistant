package com.jarves.assistant

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jarves.assistant.adapter.TaskAdapter
import com.jarves.assistant.databinding.ActivityMainBinding
import com.jarves.assistant.engine.DeviceControlExecutor
import com.jarves.assistant.engine.JarvesBrainEngine
import com.jarves.assistant.engine.TaskQueueManager
import com.jarves.assistant.model.JarvesTask
import com.jarves.assistant.service.JarvesOverlayService
import com.jarves.assistant.service.JarvesService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), TaskQueueManager.QueueListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var brainEngine: JarvesBrainEngine
    private lateinit var executor: DeviceControlExecutor
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var prefs: SharedPreferences

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                processCommandText(spokenText)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.forEach { (_, isGranted) ->
            if (!isGranted) allGranted = false
        }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("jarves_prefs", MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        brainEngine = JarvesBrainEngine(apiKey)
        executor = DeviceControlExecutor(this)

        setupRecyclerView()
        setupListeners()
        requestSystemPermissions()
        checkOverlayPermission()
        startForegroundService()

        TaskQueueManager.instance.addListener(this)
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(emptyList()) { task ->
            TaskQueueManager.instance.removeTaskById(task.id)
            Toast.makeText(this, "Task Deleted: ${task.title}", Toast.LENGTH_SHORT).show()
        }
        binding.rvTaskQueue.layoutManager = LinearLayoutManager(this)
        binding.rvTaskQueue.adapter = taskAdapter
    }

    private fun setupListeners() {
        binding.btnMicTrigger.setOnClickListener {
            launchVoiceInput()
        }

        binding.btnSendTextCommand.setOnClickListener {
            val text = binding.etCommandInput.text.toString().trim()
            if (text.isNotBlank()) {
                binding.etCommandInput.setText("")
                processCommandText(text)
            }
        }

        binding.btnApiKey.setOnClickListener {
            showApiKeyDialog()
        }

        binding.btnPermissions.setOnClickListener {
            requestSystemPermissions()
            checkOverlayPermission()
        }
    }

    private fun launchVoiceInput() {
        showOverlayListening("Say command...")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVES Listening...")
        }
        try {
            binding.tvStatus.text = getString(R.string.status_listening)
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech Recognition not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processCommandText(commandText: String) {
        binding.tvStatus.text = getString(R.string.status_processing)
        binding.tvSubStatus.text = "\"$commandText\""
        showOverlayProcessing(commandText)

        lifecycleScope.launch {
            val parsedTasks = brainEngine.parseVoiceCommand(commandText)
            binding.tvStatus.text = getString(R.string.status_idle)

            if (parsedTasks.isEmpty()) {
                executor.speak("I didn't quite catch that command.")
                return@launch
            }

            for (task in parsedTasks) {
                if (task.actionType == "DELETE_TASK") {
                    executor.execute(task)
                } else {
                    executor.speak("Received: ${task.title}")
                    TaskQueueManager.instance.addTask(this@MainActivity, task) { readyTask ->
                        executor.execute(readyTask)
                    }
                }
            }
        }
    }

    private fun showOverlayListening(hint: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            val intent = Intent(this, JarvesOverlayService::class.java).apply {
                action = "ACTION_SHOW_LISTENING"
                putExtra("EXTRA_TEXT", hint)
            }
            startService(intent)
        }
    }

    private fun showOverlayProcessing(commandText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            val intent = Intent(this, JarvesOverlayService::class.java).apply {
                action = "ACTION_SHOW_PROCESSING"
                putExtra("EXTRA_TEXT", commandText)
            }
            startService(intent)
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showApiKeyDialog() {
        val currentKey = prefs.getString("gemini_api_key", "") ?: ""
        val etKey = EditText(this).apply {
            setText(currentKey)
            hint = "Enter Google Gemini API Key"
            setPadding(40, 40, 40, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Google Gemini API Key")
            .setMessage("Set custom API key for enhanced AI comprehension:")
            .setView(etKey)
            .setPositiveButton("Save") { _, _ ->
                val newKey = etKey.text.toString().trim()
                prefs.edit().putString("gemini_api_key", newKey).apply()
                brainEngine.setApiKey(newKey)
                Toast.makeText(this, "Gemini API Key Saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestSystemPermissions() {
        val required = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startForegroundService() {
        val serviceIntent = Intent(this, JarvesService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onQueueUpdated(tasks: List<JarvesTask>) {
        runOnUiThread {
            taskAdapter.updateTasks(tasks)
            binding.tvQueueCount.text = "${tasks.size} Tasks"
            binding.tvEmptyQueue.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onTaskCompleted(task: JarvesTask) {
        // Notification handled by JarvesService
    }

    override fun onDestroy() {
        super.onDestroy()
        TaskQueueManager.instance.removeListener(this)
        executor.destroy()
    }
}
