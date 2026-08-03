package com.jarves.assistant.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.jarves.assistant.R

class JarvesOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var tvTitle: TextView? = null
    private var tvSub: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun createOverlayView() {
        if (overlayView != null) return

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 50
        }

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_dynamic_island, null)
        overlayView?.visibility = View.GONE

        tvTitle = overlayView?.findViewById(R.id.tvIslandTitle)
        tvSub = overlayView?.findViewById(R.id.tvIslandSub)

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showListening(commandHint: String = "Say command...") {
        handler.removeCallbacksAndMessages(null)
        handler.post {
            overlayView?.visibility = View.VISIBLE
            tvTitle?.text = "JARVES Listening..."
            tvSub?.text = commandHint
        }
        // 5-second silence auto-hide timer if no command is spoken
        hideOverlay(5000)
    }

    fun showProcessing(commandText: String) {
        handler.removeCallbacksAndMessages(null)
        handler.post {
            overlayView?.visibility = View.VISIBLE
            tvTitle?.text = "Processing Command"
            tvSub?.text = "\"$commandText\""
        }
        // Auto-collapses 5 seconds after speech is processed
        hideOverlay(5000)
    }

    fun hideOverlay(delayMillis: Long = 5000) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            overlayView?.visibility = View.GONE
        }, delayMillis)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val text = intent?.getStringExtra("EXTRA_TEXT") ?: ""

        when (action) {
            "ACTION_SHOW_LISTENING" -> showListening(text.ifBlank { "Say command..." })
            "ACTION_SHOW_PROCESSING" -> showProcessing(text)
            "ACTION_HIDE" -> hideOverlay(100)
            else -> showListening()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
