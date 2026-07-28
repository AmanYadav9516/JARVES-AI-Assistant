package com.jarves.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import com.jarves.assistant.engine.DeviceControlExecutor

class CallAnnouncerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            val callerName = if (!incomingNumber.isNullOrEmpty()) {
                getContactName(context, incomingNumber) ?: incomingNumber
            } else {
                "Unknown Number"
            }

            val prefs = context.getSharedPreferences("jarves_prefs", Context.MODE_PRIVATE)
            val isDrivingMode = prefs.getBoolean("driving_mode_active", false)

            if (isDrivingMode && !incomingNumber.isNullOrEmpty()) {
                // Auto-reply SMS
                try {
                    val smsManager = context.getSystemService(SmsManager::class.java)
                    smsManager.sendTextMessage(
                        incomingNumber, null,
                        "Sir is currently driving, will reply soon. - Sent by JARVES AI",
                        null, null
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val executor = DeviceControlExecutor(context)
            executor.speak("Sir, incoming call from $callerName")
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
            .appendPath(phoneNumber)
            .build()
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return null
    }
}
