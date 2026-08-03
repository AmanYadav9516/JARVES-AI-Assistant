package com.jarves.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.jarves.assistant.engine.DeviceControlExecutor

class PowerStateReceiver : BroadcastReceiver() {

    private var lastAlertTime = 0L

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val executor = DeviceControlExecutor(context)
        val currentTime = System.currentTimeMillis()

        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                executor.speak("Power Cable Connected. Charging Started.")
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                executor.speak("Power Cable Disconnected.")
            }
            Intent.ACTION_BATTERY_LOW -> {
                executor.speak("Warning Sir! Battery level is low. Please connect charger.")
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                // Throttle battery change checks to once every 2 minutes
                if (currentTime - lastAlertTime < 120000L) return

                val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                val tempCelsius = if (rawTemp > 0) rawTemp / 10.0f else 0.0f
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 0

                if (tempCelsius >= 45.0f) {
                    lastAlertTime = currentTime
                    executor.speak("Warning! Battery temperature high at ${tempCelsius} degrees Celsius. Unplug charger.")
                } else if (status == BatteryManager.BATTERY_STATUS_FULL || pct >= 100) {
                    lastAlertTime = currentTime
                    executor.speak("Battery 100 percent fully charged, Sir. Please unplug charger.")
                }
            }
        }
    }
}
