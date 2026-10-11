package com.neuralhud.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class HudOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var panelParams: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(71, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(71, buildNotification())
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permissão de sobreposição necessária.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) showBubble()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("neural_hud", "Neural HUD", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "neural_hud")
                .setContentTitle("Neural HUD ativo")
                .setContentText("Toque na esfera para abrir ou minimizar o painel.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Neural HUD ativo")
                .setContentText("Toque na esfera para abrir ou minimizar o painel.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        }
    }

    private fun showBubble() {
        val prefs = getSharedPreferences("neural_hud_settings", MODE_PRIVATE)
        val size = prefs.getInt("bubble_size", 58).coerceIn(42, 86)
        bubbleParams = WindowManager.LayoutParams(
            dp(size), dp(size),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(180)
        }
        bubble = TextView(this).apply {
            text = "N"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(0, 151, 167))
            alpha = prefs.getFloat("bubble_alpha", 0.9f)
            elevation = dp(8).toFloat()
            setOnTouchListener { _, event -> handleBubbleTouch(event) }
        }
        try {
            windowManager.addView(bubble, bubbleParams)
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível exibir o HUD: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun handleBubbleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                bubbleParams.x += (event.rawX - lastX).toInt()
                bubbleParams.y += (event.rawY - lastY).toInt()
                lastX = event.rawX
                lastY = event.rawY
                try { windowManager.updateViewLayout(bubble, bubbleParams) } catch (_: Exception) {}
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.abs(event.rawX - downX) + kotlin.math.abs(event.rawY - downY)
                if (moved < dp(10)) togglePanel()
                return true
            }
        }
        return true
    }

    private fun togglePanel() {
        if (panel != null) {
            removePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        if (panel != null) return
        val prefs = getSharedPreferences("neural_hud_settings", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(16, 24, 32))
            elevation = dp(12).toFloat()
        }
        panel = root
        val title = TextView(this).apply {
            text = "NEURAL HUD"
            setTextColor(Color.rgb(77, 208, 225))
            textSize = 17f
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(title)
        val observer = getSharedPreferences("neural_hud_observer", MODE_PRIVATE)
        val lastPackage = observer.getString("last_package", "Nenhum app observado") ?: "Nenhum app observado"
        val category = observer.getString("last_category", "Acessibilidade desligada") ?: "Acessibilidade desligada"
        val summary = TextView(this).apply {
            text = "App: $lastPackage\nCategoria local: $category"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(summary)

        panelButton(root, "Aparência / comportamento: ${prefs.getString("mode", "AUTO")}") {
            val modes = listOf("AUTO", "FIXO", "DISCRETO")
            val current = prefs.getString("mode", "AUTO") ?: "AUTO"
            val next = modes[(modes.indexOf(current).coerceAtLeast(0) + 1) % modes.size]
            prefs.edit().putString("mode", next).apply()
            applyBubbleStyle()
            removePanel()
            showPanel()
        }
        val opacityLabel = TextView(this).apply {
            text = "Opacidade"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }
        root.addView(opacityLabel)
        val opacity = SeekBar(this).apply {
            max = 100
            progress = (prefs.getFloat("bubble_alpha", 0.9f) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = (progress.coerceIn(25, 100) / 100f)
                    prefs.edit().putFloat("bubble_alpha", value).apply()
                    applyBubbleStyle()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(opacity)
        val sizeLabel = TextView(this).apply {
            text = "Tamanho da esfera"
            setTextColor(Color.LTGRAY)
            textSize = 12f
        }
        root.addView(sizeLabel)
        val sizeSeek = SeekBar(this).apply {
            max = 44
            progress = (prefs.getInt("bubble_size", 58) - 42).coerceIn(0, 44)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = 42 + progress
                    prefs.edit().putInt("bubble_size", value).apply()
                    bubbleParams.width = dp(value)
                    bubbleParams.height = dp(value)
                    try { windowManager.updateViewLayout(bubble, bubbleParams) } catch (_: Exception) {}
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(sizeSeek)
        panelButton(root, "Ensinar: app normal") {
            LocalNeuralModel(this).feedback(lastPackage, 0)
            Toast.makeText(this, "Feedback salvo no dispositivo.", Toast.LENGTH_SHORT).show()
        }
        panelButton(root, "Ensinar: possível problema") {
            LocalNeuralModel(this).feedback(lastPackage, 5)
            Toast.makeText(this, "Feedback salvo no dispositivo.", Toast.LENGTH_SHORT).show()
        }
        panelButton(root, "Abrir pesquisa e inspeção de arquivos") {
            val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        panelButton(root, "Minimizar painel") { removePanel() }
        panelButton(root, "Encerrar HUD") {
            removePanel()
            stopSelf()
        }

        panelParams = WindowManager.LayoutParams(
            dp(246), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bubbleParams.x + dp(5)).coerceAtLeast(0)
            y = (bubbleParams.y + dp(68)).coerceAtMost(resources.displayMetrics.heightPixels - dp(220))
        }
        try {
            windowManager.addView(root, panelParams!!)
        } catch (e: Exception) {
            panel = null
            Toast.makeText(this, "Não foi possível abrir o painel.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun panelButton(parent: LinearLayout, text: String, action: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(39, 69, 83))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(3) })
    }

    private fun removePanel() {
        val old = panel ?: return
        try { windowManager.removeView(old) } catch (_: Exception) {}
        panel = null
        panelParams = null
    }

    private fun applyBubbleStyle() {
        val prefs = getSharedPreferences("neural_hud_settings", MODE_PRIVATE)
        val mode = prefs.getString("mode", "AUTO") ?: "AUTO"
        val base = prefs.getFloat("bubble_alpha", 0.9f)
        val alpha = when (mode) {
            "DISCRETO" -> minOf(base, 0.48f)
            "FIXO" -> maxOf(base, 0.90f)
            else -> base
        }
        bubble?.alpha = alpha
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        removePanel()
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
