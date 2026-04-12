package fr.gaetanlhf.dreamflipclock

import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.dreams.DreamService
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.view.WindowCompat

class DreamFlipClockService : DreamService() {

    private lateinit var flipClock: FlipClockView
    private lateinit var dimOverlay: View
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlayEnabled = false

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            flipClock.updateTime()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 1000L - (now % 1000L))
        }
    }

    override fun onCreate() {
        isScreenBright = false
        super.onCreate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isFullscreen = true
        isInteractive = false
        isScreenBright = false

        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        overlayEnabled = prefs.getBoolean(SettingsActivity.KEY_OVERLAY_ENABLED, false)
        val brightness = prefs.getInt(SettingsActivity.KEY_BRIGHTNESS, SettingsActivity.DEFAULT_BRIGHTNESS)

        window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.attributes = w.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            w.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        setContentView(R.layout.dream_flip_clock)
        flipClock = findViewById(R.id.flip_clock)
        dimOverlay = findViewById(R.id.dim_overlay)
        flipClock.loadSettings(prefs)

        if (overlayEnabled && SettingsActivity.isWithinSchedule(prefs)) {
            flipClock.dimLevel = brightness
            if (brightness < 100) {
                dimOverlay.visibility = View.VISIBLE
                dimOverlay.alpha = (1f - brightness / 100f) * 0.99f
            }
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DreamFlipClock::Dream").apply {
            acquire(10 * 60 * 60 * 1000L)
        }

        if (overlayEnabled) {
            val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
            if (SettingsActivity.isWithinSchedule(prefs)) {
                val brightness = prefs.getInt(SettingsActivity.KEY_BRIGHTNESS, SettingsActivity.DEFAULT_BRIGHTNESS)
                OverlayService.instance?.showOverlay(brightness)
            }
        }

        flipClock.updateTime(animate = false)
        handler.post(updateRunnable)
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        OverlayService.instance?.hideOverlay()
        handler.removeCallbacks(updateRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(updateRunnable)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
