package com.neuralhud.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class HudOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: ImageView? = null
    private var panel: LinearLayout? = null
    private var statusView: TextView? = null
    private var commandInput: EditText? = null
    private var settingsContent: LinearLayout? = null
    private var settingsExpanded = false
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var panelParams: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("status")?.let { statusView?.text = it }
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
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permita que o Neural HUD apareça sobre outros apps.", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "O Android bloqueou o serviço: " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble == null) showBubble()
        if (intent?.getBooleanExtra("open_panel", false) == true && panel == null) showPanel()
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Neural HUD em execução", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Neural HUD ativo")
            .setContentText("Toque na esfera para abrir os comandos.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
    }

    private fun showBubble() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val size = prefs.getInt(KEY_SIZE, DEFAULT_SIZE).coerceIn(MIN_SIZE, MAX_SIZE)
        bubbleParams = WindowManager.LayoutParams(
            dp(size), dp(size),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, dp(12)).coerceAtLeast(0)
            y = prefs.getInt(KEY_Y, dp(180)).coerceAtLeast(0)
        }
        bubble = ImageView(this).apply {
            setImageResource(R.drawable.ic_neural_hud_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(Color.rgb(9, 20, 34), Color.rgb(53, 221, 235), 50, 2)
            contentDescription = "Neural HUD. Toque para abrir ou fechar o painel."
            elevation = dp(8).toFloat()
            setOnTouchListener { _, event -> handleBubbleTouch(event) }
        }
        try {
            windowManager.addView(bubble, bubbleParams)
            applyBubbleStyle()
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível exibir a esfera: " + (e.message ?: "erro do Android"), Toast.LENGTH_LONG).show()
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
                val width = resources.displayMetrics.widthPixels
                val height = resources.displayMetrics.heightPixels
                bubbleParams.x = (bubbleParams.x + (event.rawX - lastX).toInt()).coerceIn(0, (width - bubbleParams.width).coerceAtLeast(0))
                bubbleParams.y = (bubbleParams.y + (event.rawY - lastY).toInt()).coerceIn(0, (height - bubbleParams.height).coerceAtLeast(0))
                lastX = event.rawX
                lastY = event.rawY
                try { windowManager.updateViewLayout(bubble, bubbleParams) } catch (_: Exception) {}
                if (panel != null) repositionPanel()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.abs(event.rawX - downX) + kotlin.math.abs(event.rawY - downY)
                if (moved < dp(10)) togglePanel()
                else getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt(KEY_X, bubbleParams.x)
                    .putInt(KEY_Y, bubbleParams.y)
                    .apply()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun togglePanel() {
        if (panel == null) showPanel() else removePanel()
    }

    private fun showPanel() {
        if (panel != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(13, 22, 33), Color.rgb(43, 67, 84), 24, 1)
            elevation = dp(14).toFloat()
        }
        panel = root

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content, ScrollView.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(TextView(this).apply {
            text = "NEURAL HUD"
            textSize = 18f
            setTextColor(Color.rgb(91, 224, 238))
            letterSpacing = 0.06f
        })
        titleBlock.addView(TextView(this).apply {
            text = "Comandos locais • sem API"
            textSize = 11f
            setTextColor(Color.rgb(180, 200, 214))
        })
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        val close = smallButton("×") { removePanel() }
        close.contentDescription = "Fechar painel"
        header.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))
        content.addView(header)

        val status = TextView(this).apply {
            text = LocalAutomationEngine.getStatus(this@HudOverlayService)
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Color.rgb(26, 42, 54), Color.rgb(36, 66, 82), 12, 1)
            minHeight = dp(42)
            gravity = Gravity.CENTER_VERTICAL
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        statusView = status
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
            bottomMargin = dp(14)
        })

        content.addView(sectionLabel("SUA INSTRUÇÃO"))
        val input = EditText(this).apply {
            hint = "Ex.: Quando abrir YouTube, pesquise gatos"
            setHintTextColor(Color.rgb(155, 174, 189))
            setTextColor(Color.WHITE)
            textSize = 14f
            minLines = 2
            maxLines = 3
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(11), dp(10), dp(11), dp(10))
            background = rounded(Color.rgb(23, 36, 48), Color.rgb(44, 78, 95), 12, 1)
            setText(LocalAutomationEngine.getInstruction(this@HudOverlayService))
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        commandInput = input
        content.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        primaryButton(content, "Salvar e ativar") { saveInstruction() }

        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secondaryButton(firstRow, "Arquivos e ferramentas") { openTools() }
        secondaryButton(firstRow, "Acessibilidade") { openAccessibilitySettings() }
        content.addView(firstRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        secondaryButton(secondRow, "Pausar automação") {
            LocalAutomationEngine.clear(this@HudOverlayService)
            refreshStatus("Automação pausada. Salve uma instrução para executar novamente.")
        }
        secondaryButton(secondRow, "Ajustes") { toggleSettings() }
        content.addView(secondRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        settingsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Color.rgb(20, 32, 43), Color.rgb(37, 65, 80), 12, 1)
        }
        content.addView(settingsContent, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        buildSettings(settingsContent!!)

        val footer = TextView(this).apply {
            text = "Ações em outros aplicativos exigem que você ative o serviço Neural HUD nas configurações do Android."
            textSize = 11f
            setTextColor(Color.rgb(160, 183, 197))
            setPadding(dp(2), dp(12), dp(2), dp(6))
        }
        content.addView(footer)
        secondaryButton(content, "Encerrar HUD") {
            LocalAutomationEngine.clear(this@HudOverlayService)
            removePanel()
            stopSelf()
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val panelWidth = minOf(dp(352), (screenWidth - dp(24)).coerceAtLeast(dp(250)))
        val panelHeight = minOf(dp(540), (screenHeight - dp(32)).coerceAtLeast(dp(300)))
        panelParams = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
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
            settingsContent = null
            panelParams = null
            Toast.makeText(this, "Não foi possível abrir o painel: " + (e.message ?: "erro do Android"), Toast.LENGTH_LONG).show()
        }
    }

    private fun buildSettings(container: LinearLayout) {
        container.addView(sectionLabel("Opacidade"))
        val opacity = SeekBar(this).apply {
            max = 100
            progress = (getSharedPreferences(PREFS, MODE_PRIVATE).getFloat(KEY_ALPHA, 0.9f) * 100).toInt()
            contentDescription = "Opacidade da esfera"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(KEY_ALPHA, progress.coerceIn(25, 100) / 100f).apply()
                    applyBubbleStyle()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(opacity, LinearLayout.LayoutParams(-1, dp(44)))

        container.addView(sectionLabel("Tamanho"))
        val size = SeekBar(this).apply {
            max = MAX_SIZE - MIN_SIZE
            progress = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_SIZE, DEFAULT_SIZE).coerceIn(MIN_SIZE, MAX_SIZE) - MIN_SIZE
            contentDescription = "Tamanho da esfera"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = MIN_SIZE + progress
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_SIZE, value).apply()
                    bubbleParams.width = dp(value)
                    bubbleParams.height = dp(value)
                    try { windowManager.updateViewLayout(bubble, bubbleParams) } catch (_: Exception) {}
                    repositionPanel()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(size, LinearLayout.LayoutParams(-1, dp(44)))
    }

    private fun toggleSettings() {
        settingsExpanded = !settingsExpanded
        settingsContent?.visibility = if (settingsExpanded) View.VISIBLE else View.GONE
        refreshStatus(if (settingsExpanded) "Ajuste opacidade e tamanho da esfera." else "Ajustes ocultos.")
        repositionPanel()
    }

    private fun saveInstruction() {
        val result = LocalAutomationEngine.saveInstruction(this, commandInput?.text?.toString().orEmpty())
        refreshStatus(result)
        if (result.startsWith("Instrução salva")) {
            val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            commandInput?.let { manager.hideSoftInputFromWindow(it.windowToken, 0) }
            commandInput?.clearFocus()
        }
    }

    private fun openTools() {
        removePanel()
        try {
            startActivity(Intent(this, ToolboxActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir as ferramentas.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAccessibilitySettings() {
        removePanel()
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "Abra Configurações > Acessibilidade manualmente.", Toast.LENGTH_LONG).show()
        }
    }

    private fun repositionPanel() {
        val params = panelParams ?: return
        val view = panel ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val margin = dp(10)
        val bubbleRight = bubbleParams.x + bubbleParams.width
        params.x = if (bubbleRight + params.width + margin <= screenWidth) {
            bubbleRight + margin
        } else {
            bubbleParams.x - params.width - margin
        }.coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
        params.y = bubbleParams.y.coerceIn(0, (screenHeight - params.height - dp(16)).coerceAtLeast(0))
        try {
            if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}
    }

    private fun refreshStatus(value: String) {
        statusView?.text = value
        LocalAutomationEngine.setStatus(this, value)
    }

    private fun removePanel() {
        val old = panel ?: return
        try { windowManager.removeView(old) } catch (_: Exception) {}
        panel = null
        statusView = null
        commandInput = null
        settingsContent = null
        settingsExpanded = false
        panelParams = null
    }

    private fun applyBubbleStyle() {
        bubble?.alpha = getSharedPreferences(PREFS, MODE_PRIVATE).getFloat(KEY_ALPHA, 0.9f).coerceIn(0.25f, 1f)
    }

    private fun primaryButton(parent: LinearLayout, title: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 15f
            minHeight = dp(50)
            setTextColor(Color.rgb(8, 23, 32))
            background = rounded(Color.rgb(91, 224, 238), Color.rgb(91, 224, 238), 14, 0)
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(50)))
    }

    private fun secondaryButton(parent: LinearLayout, title: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 12f
            minHeight = dp(46)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(34, 51, 65), Color.rgb(56, 86, 104), 13, 1)
            setOnClickListener { action() }
        }
        val params = if (parent.orientation == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) }
        } else {
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(5) }
        }
        parent.addView(button, params)
    }

    private fun smallButton(title: String, action: () -> Unit): Button = Button(this).apply {
        text = title
        textSize = 20f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(34, 51, 65), Color.rgb(56, 86, 104), 13, 1)
        setOnClickListener { action() }
    }

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        letterSpacing = 0.04f
        setTextColor(Color.rgb(153, 198, 212))
        setPadding(dp(1), dp(5), dp(1), dp(6))
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
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

    companion object {
        private const val NOTIFICATION_ID = 71
        private const val CHANNEL_ID = "neural_hud"
        private const val PREFS = "neural_hud_settings"
        private const val KEY_SIZE = "bubble_size"
        private const val KEY_ALPHA = "bubble_alpha"
        private const val KEY_X = "bubble_x"
        private const val KEY_Y = "bubble_y"
        private const val DEFAULT_SIZE = 58
        private const val MIN_SIZE = 42
        private const val MAX_SIZE = 86
    }
}
