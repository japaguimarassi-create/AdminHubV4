package com.neuralhud.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

enum class OrbMode { IDLE, LISTENING, PROCESSING, SPEAKING }

class JarvisOrbView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var mode: OrbMode = OrbMode.IDLE
        set(value) { field = value; invalidate() }

    private var audioLevel = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun setAudioLevel(level: Float) {
        audioLevel = ((level + 2f) / 12f).coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val time = SystemClock.uptimeMillis() / 1000f
        val amount = when (mode) {
            OrbMode.IDLE -> 0.025f
            OrbMode.LISTENING -> 0.07f + audioLevel * 0.05f
            OrbMode.PROCESSING -> 0.04f
            OrbMode.SPEAKING -> 0.14f
        }
        val speed = when (mode) {
            OrbMode.IDLE -> 1.5f
            OrbMode.LISTENING -> 5.1f
            OrbMode.PROCESSING -> 3.3f
            OrbMode.SPEAKING -> 8.2f
        }
        val radius = size * 0.30f * (1f + sin(time * speed) * amount)
        val outer = radius * 1.8f
        paint.shader = RadialGradient(cx, cy, outer,
            intArrayOf(Color.argb(125, 26, 139, 255), Color.argb(57, 90, 75, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.63f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, outer, paint)
        ring.shader = null
        ring.strokeWidth = size * 0.016f
        ring.color = Color.argb(225, 69, 223, 255)
        canvas.drawCircle(cx, cy, radius * 1.28f, ring)
        ring.strokeWidth = size * 0.011f
        ring.color = Color.argb(180, 145, 105, 255)
        canvas.drawCircle(cx, cy, radius * 1.42f, ring)
        paint.shader = LinearGradient(cx - radius, cy - radius, cx + radius, cy + radius,
            intArrayOf(Color.rgb(42, 128, 255), Color.rgb(46, 224, 255), Color.rgb(123, 82, 255)),
            floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = RadialGradient(cx - radius * 0.24f, cy - radius * 0.28f, radius,
            intArrayOf(Color.argb(220, 142, 237, 255), Color.argb(90, 25, 90, 200), Color.argb(225, 7, 16, 47)),
            floatArrayOf(0f, 0.52f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 0.92f, paint)
        paint.shader = null
        ring.strokeWidth = size * 0.012f
        ring.color = Color.argb(240, 216, 251, 255)
        canvas.drawCircle(cx, cy, radius * 0.60f, ring)
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius * 0.10f, paint)
        postInvalidateDelayed(if (mode == OrbMode.IDLE) 42L else 16L)
    }
}
