package com.neuralhud.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.text.InputType

class HudOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var statusView: TextView? = null
    private var commandInput: EditText? = null
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var panelParams: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: return
            statusView?.text = status
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        val filter = IntentFilter(LocalAutomationEngine.ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
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
        if (intent?.getBooleanExtra("open_panel", false) == true && panel == null) showPanel()
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
                .setContentText("A esfera mantém o painel de comandos disponível.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Neural HUD ativo")
                .setContentText("A esfera mantém o painel de comandos disponível.")
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
            x = dp(12)
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
            contentDescription = "Neural HUD. Toque para abrir ou minimizar o painel."
            setOnTouchListener { _, event -> handleBubbleTouch(event) }
        }
        try {
            windowManager.addView(bubble, bubbleParams)
            applyBubbleStyle()
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
                if (panel != null) repositionPanel()
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
            text = "NEURAL HUD  •  IA LOCAL"
            setTextColor(Color.rgb(77, 208, 225))
            textSize = 16f
            setPadding(0, 0, 0, dp(5))
        }
        root.addView(title)

        val status = TextView(this).apply {
            text = LocalAutomationEngine.getStatus(this@HudOverlayService)
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundColor(Color.rgb(29, 43, 54))
        }
        statusView = status
        root.addView(status, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(6)
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(controls, FrameLayout.LayoutParams(-1, -2))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        panelButton(controls, "Aparência: ${prefs.getString("mode", "AUTO")}") {
            val modes = listOf("AUTO", "FIXO", "DISCRETO")
            val current = prefs.getString("mode", "AUTO") ?: "AUTO"
            val next = modes[(modes.indexOf(current).coerceAtLeast(0) + 1) % modes.size]
            prefs.edit().putString("mode", next).apply()
            applyBubbleStyle()
            refreshStatus("Modo de aparência: $next")
        }

        textLabel(controls, "Opacidade da esfera")
        val opacity = SeekBar(this).apply {
            max = 100
            progress = (prefs.getFloat("bubble_alpha", 0.9f) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress.coerceIn(25, 100) / 100f
                    prefs.edit().putFloat("bubble_alpha", value).apply()
                    applyBubbleStyle()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        controls.addView(opacity, LinearLayout.LayoutParams(-1, dp(32)))

        textLabel(controls, "Tamanho da esfera")
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
        controls.addView(sizeSeek, LinearLayout.LayoutParams(-1, dp(32)))

        panelButton(controls, "Ativar IA nos aplicativos (Acessibilidade)") {
            refreshStatus("Ative o serviço Neural HUD nas configurações de Acessibilidade do Android. O Android exige sua autorização.")
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(this, "Abra Configurações > Acessibilidade manualmente.", Toast.LENGTH_LONG).show()
            }
        }
        panelButton(controls, "Pausar automação") {
            LocalAutomationEngine.clear(this)
            refreshStatus("Automação pausada.")
        }
        panelButton(controls, "Fechar painel") { removePanel() }
        panelButton(controls, "Encerrar HUD") {
            removePanel()
            stopSelf()
        }

        val commandLabel = TextView(this).apply {
            text = "O QUE VOCÊ QUER QUE A IA FAÇA?"
            setTextColor(Color.rgb(77, 208, 225))
            textSize = 12f
            setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(commandLabel)

        val input = EditText(this).apply {
            hint = "Quando abrir YouTube, pesquise gatos"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            minLines = 2
            maxLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(9), dp(7), dp(9), dp(7))
            setBackgroundColor(Color.rgb(29, 43, 54))
            setText(LocalAutomationEngine.getInstruction(this@HudOverlayService))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        commandInput = input
        root.addView(input, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        panelButton(root, "Salvar e ativar instrução") {
            val raw = commandInput?.text?.toString().orEmpty()
            val result = LocalAutomationEngine.saveInstruction(this, raw)
            refreshStatus(result)
            if (result.startsWith("Instrução salva")) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(commandInput?.windowToken, 0)
                commandInput?.clearFocus()
            }
        }

        val screenHeight = resources.displayMetrics.heightPixels
        val requestedHeight = minOf(dp(610), screenHeight - dp(36)).coerceAtLeast(dp(350))
        panelParams = WindowManager.LayoutParams(
            dp(318), requestedHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        }
        repositionPanel()
        try {
            windowManager.addView(root, panelParams!!)
        } catch (e: Exception) {
            panel = null
            statusView = null
            commandInput = null
            Toast.makeText(this, "Não foi possível abrir o painel: ${e.message ?: "erro do Android"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun repositionPanel() {
        val params = panelParams ?: return
        val panelView = panel ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        params.x = (bubbleParams.x + bubbleParams.width + dp(5)).coerceAtMost((screenWidth - params.width - dp(8)).coerceAtLeast(dp(8)))
        if (params.x < dp(8)) params.x = dp(8)
        params.y = (bubbleParams.y + dp(10)).coerceAtMost((screenHeight - params.height - dp(12)).coerceAtLeast(dp(8)))
        params.y = params.y.coerceAtLeast(dp(8))
        try {
            if (panelView.isAttachedToWindow) windowManager.updateViewLayout(panelView, params)
        } catch (_: Exception) {}
    }

    private fun panelButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(39, 69, 83))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(40)).apply {
            topMargin = dp(3)
        })
    }

    private fun textLabel(parent: LinearLayout, label: String) {
        val view = TextView(this).apply {
            text = label
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        parent.addView(view)
    }

    private fun refreshStatus(text: String) {
        statusView?.text = text
        LocalAutomationEngine.setStatus(this, text)
    }

    private fun removePanel() {
        val old = panel ?: return
        try { windowManager.removeView(old) } catch (_: Exception) {}
        panel = null
        statusView = null
        commandInput = null
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
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        removePanel()
        bubble?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
