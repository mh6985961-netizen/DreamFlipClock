package fr.gaetanlhf.dreamflipclock

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import java.util.Calendar
import java.util.Locale

class FlipClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val clockTypeface: Typeface =
        resources.getFont(R.font.flipclock)

    var use24h = true
    var clockSizePercent = 100
    var oledProtection = true
    var forceOrientation = SettingsActivity.ORIENTATION_AUTO
    var dimLevel = 100
        set(value) {
            field = value
            cacheW = 0
            invalidate()
        }

    private var driftX = 0f
    private var driftY = 0f
    private var lastDriftTime = 0L
    private var amPm = ""

    private val accentColor: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.getColor(context, android.R.color.system_accent1_200)
    } else {
        0xFF90CAF9.toInt()
    }

    private class Panel {
        var current = ""
        var next = ""
        var progress = 0f
        var flipping = false
        var animator: ValueAnimator? = null
        var curTop: Bitmap? = null
        var curBot: Bitmap? = null
        var nxtTop: Bitmap? = null
        var nxtBot: Bitmap? = null
        var nxtBotFlip: Bitmap? = null
    }

    private val hoursPanel = Panel()
    private val minutesPanel = Panel()

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint().apply { style = Paint.Style.FILL }

    private val camera = Camera()
    private val mtx = Matrix()
    private var cacheW = 0; private var cacheH = 0

    fun loadSettings(prefs: SharedPreferences) {
        use24h = prefs.getBoolean(SettingsActivity.KEY_USE_24H, true)
        clockSizePercent = prefs.getInt(SettingsActivity.KEY_CLOCK_SIZE, 100)
        oledProtection = prefs.getBoolean(SettingsActivity.KEY_OLED_PROTECTION, true)
        forceOrientation = prefs.getInt(SettingsActivity.KEY_ORIENTATION, SettingsActivity.ORIENTATION_AUTO)
        cacheW = 0; invalidate()
    }

    fun updateTime(animate: Boolean = true) {
        val cal = Calendar.getInstance()
        val hour: Int
        if (use24h) {
            hour = cal.get(Calendar.HOUR_OF_DAY); amPm = ""
        } else {
            val h = cal.get(Calendar.HOUR)
            hour = if (h == 0) 12 else h
            amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        }
        val hStr = if (use24h) String.format(Locale.US, "%02d", hour) else hour.toString()
        val mStr = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))

        updatePanel(hoursPanel, hStr, animate)
        updatePanel(minutesPanel, mStr, animate)
    }

    private fun updatePanel(p: Panel, value: String, animate: Boolean) {
        if (value != p.current && !(p.flipping && p.next == value)) {
            if (animate && p.current.isNotEmpty()) {
                p.next = value; renderNext(p); startFlip(p)
            } else {
                p.current = value; renderCurrent(p); invalidate()
            }
        }
    }

    private fun scaleColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun dimColor(color: Int): Int = scaleColor(color, dimLevel / 100f)

    private fun tintWithAccent(color: Int, amount: Float): Int =
        ColorUtils.blendARGB(color, accentColor, amount)

    private fun renderHalf(panel: Panel, w: Int, h: Int, isTop: Boolean, text: String): Bitmap {
        val full = createBitmap(w, h)
        val c = Canvas(full)
        val cr = h * CORNER_RADIUS_RATIO
        val baseCard = tintWithAccent(if (isTop) CARD_TOP_COLOR else CARD_BOT_COLOR, 0.10f)
        val bgColor = dimColor(baseCard)
        val txtColor = dimColor(tintWithAccent(TEXT_COLOR, 0.08f))

        c.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), cr, cr,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor; style = Paint.Style.FILL })

        if (text.isNotBlank()) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = txtColor; typeface = clockTypeface
                textSize = h * TEXT_SIZE_RATIO; textAlign = Paint.Align.CENTER
            }
            val baseline = h / 2f + 369.5f * tp.textSize / 1000f
            c.drawText(text, w / 2f, baseline, tp)

            if (panel === hoursPanel && amPm.isNotEmpty()) {
                val apPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    color = txtColor; typeface = clockTypeface
                    textSize = h * 0.09f; textAlign = Paint.Align.LEFT
                }
                c.drawText(amPm, h * 0.07f, h * 0.14f, apPaint)
            }
        }

        val halfH = h / 2
        val result = if (isTop) Bitmap.createBitmap(full, 0, 0, w, halfH)
        else Bitmap.createBitmap(full, 0, halfH, w, h - halfH)
        full.recycle()
        return result
    }

    private fun renderCurrent(p: Panel) {
        if (cacheW <= 0) return
        p.curTop?.recycle(); p.curBot?.recycle()
        p.curTop = renderHalf(p, cacheW, cacheH, true, p.current)
        p.curBot = renderHalf(p, cacheW, cacheH, false, p.current)
    }

    private fun renderNext(p: Panel) {
        if (cacheW <= 0) return
        p.nxtTop?.recycle(); p.nxtBot?.recycle(); p.nxtBotFlip?.recycle()
        p.nxtTop = renderHalf(p, cacheW, cacheH, true, p.next)
        p.nxtBot = renderHalf(p, cacheW, cacheH, false, p.next)
        val bot = p.nxtBot ?: return
        val fm = Matrix().apply { setScale(1f, -1f, bot.width / 2f, bot.height / 2f) }
        p.nxtBotFlip = Bitmap.createBitmap(bot, 0, 0, bot.width, bot.height, fm, true)
    }

    private fun renderAll() {
        renderCurrent(hoursPanel); renderCurrent(minutesPanel)
        if (hoursPanel.flipping) renderNext(hoursPanel)
        if (minutesPanel.flipping) renderNext(minutesPanel)
    }

    private fun startFlip(p: Panel) {
        p.animator?.cancel()
        p.flipping = true; p.progress = 0f
        p.animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FLIP_DURATION_MS
            var cancelled = false
            addUpdateListener { p.progress = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationCancel(a: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(a: android.animation.Animator) {
                    if (!cancelled) {
                        p.flipping = false; p.current = p.next; p.progress = 0f
                        p.curTop?.recycle(); p.curBot?.recycle()
                        p.curTop = p.nxtTop; p.curBot = p.nxtBot
                        p.nxtTop = null; p.nxtBot = null
                        p.nxtBotFlip?.recycle(); p.nxtBotFlip = null
                        invalidate()
                    }
                }
            })
            start()
        }
    }

    private fun updateDrift() {
        if (!oledProtection) { driftX = 0f; driftY = 0f; return }
        val now = System.currentTimeMillis()
        if (now - lastDriftTime < DRIFT_INTERVAL_MS) return
        lastDriftTime = now
        val step = (now / DRIFT_INTERVAL_MS).toInt()
        val phaseX = (step * 0.618033988749895) % 1.0
        val phaseY = (step * 0.381966011250105) % 1.0
        driftX = (kotlin.math.sin(phaseX * 2 * Math.PI) * DRIFT_AMPLITUDE).toFloat()
        driftY = (kotlin.math.cos(phaseY * 2 * Math.PI) * DRIFT_AMPLITUDE).toFloat()
    }

    private fun needsRotation(): Boolean {
        val pp = height > width
        return when (forceOrientation) {
            SettingsActivity.ORIENTATION_LANDSCAPE -> pp
            SettingsActivity.ORIENTATION_PORTRAIT -> !pp
            else -> false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateDrift()

        val rot = needsRotation()
        val vw: Float; val vh: Float
        if (rot) {
            canvas.save()
            canvas.rotate(90f, width / 2f, height / 2f)
            canvas.translate((width - height) / 2f, (height - width) / 2f)
            vw = height.toFloat(); vh = width.toFloat()
        } else { vw = width.toFloat(); vh = height.toFloat() }

        val portrait = when (forceOrientation) {
            SettingsActivity.ORIENTATION_LANDSCAPE -> false
            SettingsActivity.ORIENTATION_PORTRAIT -> true
            else -> vh > vw
        }
        val ss = clockSizePercent / 100f

        val cardW: Float; val cardH: Float; val panelGap: Float

        if (portrait) {
            cardW = vw * 0.85f * ss
            cardH = cardW / CARD_ASPECT
            panelGap = cardH * 0.08f
            val totalH = cardH * 2f + panelGap
            val scale = if (totalH > vh * 0.80f) (vh * 0.80f) / totalH else 1f
            if (scale < 1f) {
                drawPanels(canvas, vw, vh, cardW * scale, cardH * scale, panelGap * scale, true)
            } else {
                drawPanels(canvas, vw, vh, cardW, cardH, panelGap, true)
            }
        } else {
            cardH = vh * CARD_HEIGHT_RATIO * ss
            cardW = cardH * CARD_ASPECT
            panelGap = cardW * 0.10f
            val totalW = cardW * 2f + panelGap
            val scale = if (totalW > vw * 0.92f) (vw * 0.92f) / totalW else 1f
            if (scale < 1f) {
                drawPanels(canvas, vw, vh, cardW * scale, cardH * scale, panelGap * scale, false)
            } else {
                drawPanels(canvas, vw, vh, cardW, cardH, panelGap, false)
            }
        }

        if (rot) canvas.restore()
    }

    private fun drawPanels(
        canvas: Canvas, vw: Float, vh: Float,
        cardW: Float, cardH: Float, panelGap: Float, portrait: Boolean
    ) {
        val cw = cardW.toInt().coerceAtLeast(1); val ch = cardH.toInt().coerceAtLeast(2)
        if (cw != cacheW || ch != cacheH) { cacheW = cw; cacheH = ch; renderAll() }

        val gap = cardH * SPLIT_GAP_RATIO
        val halfH = cardH / 2f

        val hx: Float; val hy: Float; val mx: Float; val my: Float
        if (portrait) {
            val totalH = cardH * 2f + panelGap
            hx = (vw - cardW) / 2f + driftX; hy = (vh - totalH) / 2f + driftY
            mx = hx; my = hy + cardH + panelGap
        } else {
            val totalW = cardW * 2f + panelGap
            hx = (vw - totalW) / 2f + driftX; hy = (vh - cardH) / 2f + driftY
            mx = hx + cardW + panelGap; my = hy
        }

        drawPanel(canvas, hoursPanel, hx, hy, cardW, halfH, gap)
        drawPanel(canvas, minutesPanel, mx, my, cardW, halfH, gap)
    }

    private fun drawPanel(canvas: Canvas, p: Panel, x: Float, y: Float, w: Float, halfH: Float, gap: Float) {
        if (p.flipping) drawFlip(canvas, p, x, y, w, halfH, gap)
        else drawStatic(canvas, p, x, y, halfH, gap)
    }

    private fun drawStatic(canvas: Canvas, p: Panel, x: Float, y: Float, halfH: Float, gap: Float) {
        p.curTop?.let { canvas.drawBitmap(it, x, y, bmpPaint) }
        p.curBot?.let { canvas.drawBitmap(it, x, y + halfH + gap, bmpPaint) }
    }

    private fun drawFlip(canvas: Canvas, p: Panel, x: Float, y: Float, w: Float, halfH: Float, gap: Float) {
        if (p.progress < 0.01f) { drawStatic(canvas, p, x, y, halfH, gap); return }

        val pivotY = y + halfH
        val botY = pivotY + gap
        val angle = p.progress * 180f

        p.nxtTop?.let { canvas.drawBitmap(it, x, y, bmpPaint) }
        p.curBot?.let { canvas.drawBitmap(it, x, botY, bmpPaint) }

        if (angle > 20f) {
            val t = (angle - 20f) / 160f
            shadowPaint.color = Color.argb((t * t * 230).toInt().coerceIn(0, 255), 0, 0, 0)
            canvas.drawRect(x, botY, x + w, botY + halfH, shadowPaint)
        }

        if (angle <= 90f) {
            canvas.withClip(x, y, x + w, pivotY) {
                applyRotation(this, angle, x, w, pivotY)
                p.curTop?.let { drawBitmap(it, x, y, bmpPaint) }
            }
        } else {
            canvas.withClip(x, botY, x + w, botY + halfH) {
                applyRotation(this, angle, x, w, pivotY)
                p.nxtBotFlip?.let { drawBitmap(it, x, y - gap, bmpPaint) }
            }
        }
    }

    private fun applyRotation(canvas: Canvas, angle: Float, x: Float, w: Float, pivotY: Float) {
        camera.save()
        camera.setLocation(0f, 0f, -CAMERA_DIST * (w / 300f))
        camera.rotateX(angle)
        camera.getMatrix(mtx)
        camera.restore()
        mtx.preTranslate(-(x + w / 2f), -pivotY)
        mtx.postTranslate(x + w / 2f, pivotY)
        canvas.concat(mtx)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        for (p in arrayOf(hoursPanel, minutesPanel)) {
            p.animator?.cancel()
            p.curTop?.recycle(); p.curBot?.recycle()
            p.nxtTop?.recycle(); p.nxtBot?.recycle(); p.nxtBotFlip?.recycle()
        }
    }

    companion object {
        private const val CARD_TOP_COLOR = 0xFF292929.toInt()
        private const val CARD_BOT_COLOR = 0xFF333333.toInt()
        private const val TEXT_COLOR = 0xFFB5B5B5.toInt()

        private const val CARD_ASPECT = 479f / 600f
        private const val CARD_HEIGHT_RATIO = 600f / 676f
        private const val CORNER_RADIUS_RATIO = 60f / 600f
        private const val SPLIT_GAP_RATIO = 0.008f
        private const val TEXT_SIZE_RATIO = 0.82f

        private const val FLIP_DURATION_MS = 300L
        private const val CAMERA_DIST = 48f

        private const val DRIFT_AMPLITUDE = 5f
        private const val DRIFT_INTERVAL_MS = 60_000L
    }
}
