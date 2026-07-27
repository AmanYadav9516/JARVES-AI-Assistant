package com.jarves.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.jarves.assistant.engine.DeviceControlExecutor
import com.jarves.assistant.model.JarvesTask

class BatteryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        if (level > 0 && scale > 0) {
            val batteryPct = level * 100 / scale.toFloat()
            if (batteryPct <= 15.0f && intent.action == Intent.ACTION_BATTERY_LOW) {
                val executor = DeviceControlExecutor(context)
                executor.execute(
                    JarvesTask(
                        title = "Battery Saver Trigger",
                        actionType = "BATTERY"
                    )
                )
            }
        }
    }
}
