package fr.gaetanlhf.dreamflipclock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

class OverlayService : AccessibilityService() {

    private var overlayContainer: FrameLayout? = null
    private var clockView: FlipClockView? = null
    private var windowManager: WindowManager? = null

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            clockView?.updateTime()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 1000L - (now % 1000L))
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 0
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    fun showOverlay(brightness: Int) {
        if (overlayContainer != null) return
        val wm = windowManager ?: return

        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)

        val alpha = if (brightness < 100) {
            (((1f - brightness / 100f) * 0.99f) * 255).toInt().coerceIn(0, 255)
        } else 0

        overlayContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(alpha, 0, 0, 0))
        }

        clockView = FlipClockView(this).also { clock ->
            clock.loadSettings(prefs)
            clock.dimLevel = brightness
            clock.updateTime(animate = false)
            overlayContainer!!.addView(clock, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        try {
            wm.addView(overlayContainer, params)
            handler.post(updateRunnable)
        } catch (_: Exception) {
            overlayContainer = null
            clockView = null
        }
    }

    fun hideOverlay() {
        handler.removeCallbacks(updateRunnable)
        val container = overlayContainer ?: return
        val wm = windowManager
        val duration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
        container.animate()
            .alpha(0f)
            .setDuration(duration)
            .withEndAction {
                try { wm?.removeView(container) } catch (_: Exception) {}
                overlayContainer = null
                clockView = null
            }
            .start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        instance = null
        super.onDestroy()
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: OverlayService? = null
    }
}
