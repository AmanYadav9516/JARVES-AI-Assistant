package com.jarves.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.jarves.assistant.engine.DeviceControlExecutor

class PowerStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val executor = DeviceControlExecutor(context)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                executor.speak("Power connected, Sir. Charging started.")
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                executor.speak("Charger disconnected, Sir.")
            }
            Intent.ACTION_BATTERY_LOW -> {
                executor.speak("Warning, Sir. Battery is below 15 percent. Enabling battery saver.")
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isFull = status == BatteryManager.BATTERY_STATUS_FULL
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = level * 100 / scale.toFloat()

                if (isFull || pct >= 100.0f) {
                    executor.speak("Battery fully charged 100 percent, Sir.")
                }
            }
        }
    }
}
