package fr.gaetanlhf.dreamflipclock

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_back)
            .setOnClickListener { finish() }

        setupTimeFormat()
        setupOrientation()
        setupClockSize()
        setupOled()
        setupOverlay()
        setupBrightness()
        setupSchedule()
        setupAbout()

        setOverlayDependentCardsEnabled(
            prefs.getBoolean(KEY_OVERLAY_ENABLED, false),
            animate = false
        )
    }

    override fun onResume() {
        super.onResume()
        val switch = findViewById<MaterialSwitch>(R.id.switch_overlay)
        if (switch.isChecked && OverlayService.instance == null) {
            switch.isChecked = false
            prefs.edit { putBoolean(KEY_OVERLAY_ENABLED, false) }
            setOverlayDependentCardsEnabled(false)
        }
    }

    private fun setupTimeFormat() {
        val radioGroup = findViewById<RadioGroup>(R.id.radio_time_format)
        val use24h = prefs.getBoolean(KEY_USE_24H, true)
        radioGroup.check(if (use24h) R.id.radio_24h else R.id.radio_12h)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.edit { putBoolean(KEY_USE_24H, checkedId == R.id.radio_24h) }
        }
    }

    private fun setupOrientation() {
        val radioGroup = findViewById<RadioGroup>(R.id.radio_orientation)
        val current = prefs.getInt(KEY_ORIENTATION, ORIENTATION_AUTO)
        radioGroup.check(when (current) {
            ORIENTATION_LANDSCAPE -> R.id.radio_landscape
            ORIENTATION_PORTRAIT -> R.id.radio_portrait
            else -> R.id.radio_auto
        })
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_landscape -> ORIENTATION_LANDSCAPE
                R.id.radio_portrait -> ORIENTATION_PORTRAIT
                else -> ORIENTATION_AUTO
            }
            prefs.edit { putInt(KEY_ORIENTATION, value) }
        }
    }

    private fun setupClockSize() {
        val slider = findViewById<Slider>(R.id.slider_clock_size)
        val label = findViewById<TextView>(R.id.label_clock_size)
        val current = prefs.getInt(KEY_CLOCK_SIZE, 100)
        slider.value = current.toFloat()
        label.text = getString(R.string.percent_format, current)
        slider.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            label.text = getString(R.string.percent_format, v)
            prefs.edit { putInt(KEY_CLOCK_SIZE, v) }
        }
    }

    private fun setupBrightness() {
        val slider = findViewById<Slider>(R.id.slider_brightness)
        val value = findViewById<TextView>(R.id.label_brightness_value)
        val label = findViewById<TextView>(R.id.label_brightness)

        val current = prefs.getInt(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS).coerceIn(1, 100)
        slider.value = current.toFloat()
        value.text = getString(R.string.percent_format, current)
        label.text = brightnessLabel(current)

        slider.addOnChangeListener { _, v, _ ->
            val iv = v.toInt()
            value.text = getString(R.string.percent_format, iv)
            label.text = brightnessLabel(iv)
            prefs.edit { putInt(KEY_BRIGHTNESS, iv) }
        }
    }

    private fun setOverlayDependentCardsEnabled(enabled: Boolean, animate: Boolean = true) {
        setCardEnabled(findViewById(R.id.card_brightness), enabled, animate)
        setCardEnabled(findViewById(R.id.card_schedule), enabled, animate)
        val scheduleOn = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
        setCardEnabled(findViewById(R.id.card_schedule_times), enabled && scheduleOn, animate)
    }

    private fun setCardEnabled(card: MaterialCardView, enabled: Boolean, animate: Boolean = true) {
        val targetAlpha = if (enabled) 1f else 0.38f
        if (animate) {
            card.animate().alpha(targetAlpha).setDuration(260).start()
        } else {
            card.alpha = targetAlpha
        }
        setDescendantsEnabled(card, enabled)
    }

    private fun setDescendantsEnabled(root: View, enabled: Boolean) {
        root.isEnabled = enabled
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                setDescendantsEnabled(root.getChildAt(i), enabled)
            }
        }
    }

    private fun setupOled() {
        val switch = findViewById<MaterialSwitch>(R.id.switch_oled)
        switch.isChecked = prefs.getBoolean(KEY_OLED_PROTECTION, true)
        switch.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean(KEY_OLED_PROTECTION, checked) }
        }
        findViewById<MaterialCardView>(R.id.card_oled).setOnClickListener {
            switch.toggle()
        }
    }

    private fun setupOverlay() {
        val switch = findViewById<MaterialSwitch>(R.id.switch_overlay)
        val overlayEnabled = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
        switch.isChecked = overlayEnabled

        switch.setOnCheckedChangeListener { _, checked ->
            if (checked && OverlayService.instance == null) {
                switch.isChecked = false
                Toast.makeText(this, R.string.enable_accessibility_first, Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                prefs.edit { putBoolean(KEY_OVERLAY_ENABLED, checked) }
                setOverlayDependentCardsEnabled(checked)
            }
        }
        findViewById<MaterialCardView>(R.id.card_overlay).setOnClickListener {
            switch.toggle()
        }
    }

    private fun setupSchedule() {
        val switch = findViewById<MaterialSwitch>(R.id.switch_schedule)
        val btnFrom = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_schedule_from)
        val btnTo = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_schedule_to)

        val enabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
        switch.isChecked = enabled

        findViewById<MaterialCardView>(R.id.card_schedule).setOnClickListener {
            switch.toggle()
        }

        updateScheduleButtonText(btnFrom, prefs.getInt(KEY_SCHEDULE_FROM, 22 * 60))
        updateScheduleButtonText(btnTo, prefs.getInt(KEY_SCHEDULE_TO, 7 * 60))

        switch.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean(KEY_SCHEDULE_ENABLED, checked) }
            val overlayOn = prefs.getBoolean(KEY_OVERLAY_ENABLED, false)
            setCardEnabled(findViewById(R.id.card_schedule_times), overlayOn && checked)
        }

        btnFrom.setOnClickListener {
            pickTime(prefs.getInt(KEY_SCHEDULE_FROM, 22 * 60)) { mins ->
                prefs.edit { putInt(KEY_SCHEDULE_FROM, mins) }
                updateScheduleButtonText(btnFrom, mins)
            }
        }
        btnTo.setOnClickListener {
            pickTime(prefs.getInt(KEY_SCHEDULE_TO, 7 * 60)) { mins ->
                prefs.edit { putInt(KEY_SCHEDULE_TO, mins) }
                updateScheduleButtonText(btnTo, mins)
            }
        }
    }

    private fun updateScheduleButtonText(btn: com.google.android.material.button.MaterialButton, mins: Int) {
        val h = mins / 60
        val m = mins % 60
        btn.text = String.format(Locale.US, "%02d:%02d", h, m)
    }

    private fun pickTime(currentMinutes: Int, onPicked: (Int) -> Unit) {
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(currentMinutes / 60)
            .setMinute(currentMinutes % 60)
            .build()
        picker.addOnPositiveButtonClickListener {
            onPicked(picker.hour * 60 + picker.minute)
        }
        picker.show(supportFragmentManager, "time_picker")
    }

    private fun brightnessLabel(value: Int): String {
        val reduction = 100 - value
        return if (reduction == 0) getString(R.string.brightness_normal)
        else getString(R.string.brightness_dimmer, reduction)
    }

    private fun setupAbout() {
        findViewById<MaterialCardView>(R.id.card_about).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    companion object {
        const val PREFS_NAME = "flipnight_prefs"
        const val KEY_USE_24H = "use_24h"
        const val KEY_CLOCK_SIZE = "clock_size"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_OLED_PROTECTION = "oled_protection"
        const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        const val KEY_ORIENTATION = "orientation"
        const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        const val KEY_SCHEDULE_FROM = "schedule_from"
        const val KEY_SCHEDULE_TO = "schedule_to"

        const val DEFAULT_BRIGHTNESS = 100

        const val ORIENTATION_AUTO = 0
        const val ORIENTATION_LANDSCAPE = 1
        const val ORIENTATION_PORTRAIT = 2

        fun isWithinSchedule(prefs: SharedPreferences): Boolean {
            if (!prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)) return true
            val from = prefs.getInt(KEY_SCHEDULE_FROM, 22 * 60)
            val to = prefs.getInt(KEY_SCHEDULE_TO, 7 * 60)
            val cal = java.util.Calendar.getInstance()
            val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            return if (from <= to) now in from..to
            else now >= from || now <= to
        }
    }
}
