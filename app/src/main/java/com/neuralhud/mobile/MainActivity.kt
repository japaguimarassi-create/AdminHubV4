package com.neuralhud.mobile

import android.app.Activity
import android.app.role.RoleManager
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private var permissionScreenOpened = false
    private var hudLaunched = false
    private var configureAssistantScreen = false
    private var assistantInvocation = false
    private var assistantInput: EditText? = null
    private var assistantRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 22, 33)
        window.navigationBarColor = Color.rgb(13, 22, 33)
        JarvisLocalBrain.prewarm(this)
        configureAssistantScreen = intent.getBooleanExtra(EXTRA_CONFIGURE_ASSISTANT, false)
        assistantInvocation = intent.action == Intent.ACTION_ASSIST
        renderScreen()

        if (assistantInvocation) {
            return
        } else if (configureAssistantScreen) {
            status.text = "Configure J.A.R.V.I.S. como assistente padrão do Android. O sistema pedirá sua confirmação."
        } else if (Settings.canDrawOverlays(this)) {
            launchHud()
        } else {
            requestOverlayPermission()
        }
    }

    private fun renderAssistantScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            setBackgroundColor(Color.rgb(13, 22, 33))
        }
        page.addView(TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 26f
            setTextColor(Color.rgb(91, 224, 238))
        })
        page.addView(TextView(this).apply {
            text = "Assistente do celular • comandos locais"
            textSize = 13f
            setTextColor(Color.rgb(174, 194, 207))
            setPadding(0, dp(4), 0, dp(18))
        })
        val statusView = TextView(this).apply {
            text = "O que você quer fazer?"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(20, 32, 43))
        }
        status = statusView
        page.addView(statusView, matchWrap())

        val input = EditText(this).apply {
            hint = "Ex.: abra YouTube e pesquise gatos"
            setHintTextColor(Color.rgb(145, 167, 181))
            setTextColor(Color.WHITE)
            textSize = 15f
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(23, 36, 48))
        }
        assistantInput = input
        page.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        addButton(page, "Executar comando", true) {
            val command = assistantInput?.text?.toString().orEmpty()
            val result = JarvisCommandRouter.execute(this, command)
            status.text = result
            LocalAutomationEngine.setStatus(this, result)
        }
        addButton(page, "Ouvir offline", false) { startAssistantVoiceInput() }
        addButton(page, "Abrir painel flutuante", false) {
            if (Settings.canDrawOverlays(this)) {
                val hud = Intent(this, HudOverlayService::class.java)
                    .putExtra("open_panel", true)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(hud) else startService(hud)
                    finish()
                } catch (e: Exception) {
                    status.text = "Não foi possível abrir o HUD: " + (e.message ?: e.javaClass.simpleName)
                }
            } else {
                status.text = "Permita sobreposição nas configurações do Android para abrir o HUD."
                requestOverlayPermission()
            }
        }
        page.addView(TextView(this).apply {
            text = "Sem API de IA. O reconhecimento de fala depende de um modelo offline instalado. Para ações dentro de outros apps, ative Acessibilidade."
            textSize = 11f
            setTextColor(Color.rgb(145, 167, 181))
            setPadding(0, dp(14), 0, 0)
        }, matchWrap())
        SystemBarInsets.apply(page)
        setContentView(page)
    }

    private fun startAssistantVoiceInput() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            status.text = "Este Android não oferece o reconhecedor local usado aqui. Digite o comando."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_ASSISTANT_VOICE)
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            status.text = "Nenhum reconhecedor offline está disponível para este dispositivo. Digite o comando."
            return
        }
        releaseAssistantRecognizer()
        try {
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            assistantRecognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "Estou ouvindo…" }
                override fun onBeginningOfSpeech() { status.text = "Reconhecendo fala localmente…" }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "Processando fala…" }
                override fun onError(error: Int) {
                    status.text = "Falha no reconhecimento offline (código " + error + "). Digite ou tente novamente."
                    releaseAssistantRecognizer()
                }
                override fun onResults(results: Bundle?) {
                    val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (transcript.isNotBlank()) {
                        assistantInput?.setText(transcript)
                        assistantInput?.setSelection(transcript.length)
                        status.text = "Comando reconhecido. Revise e toque em Executar comando."
                    } else {
                        status.text = "Não consegui entender. Digite o comando."
                    }
                    releaseAssistantRecognizer()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val recognitionIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speech.startListening(recognitionIntent)
        } catch (e: Exception) {
            status.text = "Não consegui iniciar a voz offline: " + (e.message ?: e.javaClass.simpleName)
            releaseAssistantRecognizer()
        }
    }

    private fun releaseAssistantRecognizer() {
        try { assistantRecognizer?.cancel() } catch (_: Exception) {}
        try { assistantRecognizer?.destroy() } catch (_: Exception) {}
        assistantRecognizer = null
    }

    private fun renderScreen() {
        if (assistantInvocation) {
            renderAssistantScreen()
            return
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(13, 22, 33))
        }
        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = "Símbolo Neural HUD"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        page.addView(icon, LinearLayout.LayoutParams(dp(82), dp(82)).apply { bottomMargin = dp(16) })

        page.addView(TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(91, 224, 238))
        }, matchWrap())
        page.addView(TextView(this).apply {
            text = "Assistente local, comandos globais e HUD flutuante."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(174, 194, 207))
            setPadding(0, dp(8), 0, dp(24))
        }, matchWrap())

        status = TextView(this).apply {
            text = "O Android precisa autorizar a esfera flutuante."
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.rgb(20, 32, 43))
        }
        page.addView(status, matchWrap())
        addButton(page, "Permitir e continuar", true) { requestOverlayPermission() }
        addButton(page, "Definir J.A.R.V.I.S. como assistente", true) {
            requestAssistantRole()
        }
        addButton(page, "Ativar microfone para voz offline", false) {
            requestMicrophonePermission()
        }
        addButton(page, "Configurar automação", false) {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Abra Configurações > Acessibilidade.", Toast.LENGTH_LONG).show()
            }
        }
        page.addView(TextView(this).apply {
            text = "A automação entre aplicativos é opcional e só funciona após ativação manual em Acessibilidade. Os comandos são interpretados localmente."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(145, 167, 181))
            setPadding(0, dp(20), 0, 0)
        }, matchWrap())

        SystemBarInsets.apply(page)
        setContentView(page)
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            launchHud()
            return
        }
        permissionScreenOpened = true
        status.text = "Na tela do Android, habilite “Permitir sobreposição” para o Neural HUD. Depois, volte ao app."
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (e: Exception) {
                status.text = "Não consegui abrir a configuração: " + (e.message ?: "erro do Android")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (assistantInvocation) return
        if (::status.isInitialized && !configureAssistantScreen && Settings.canDrawOverlays(this) && !hudLaunched) {
            launchHud()
        } else if (::status.isInitialized && permissionScreenOpened && !Settings.canDrawOverlays(this)) {
            status.text = "A permissão continua desligada. Toque em “Permitir e continuar” para tentar novamente."
        }
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status.text = "Este Android não oferece a seleção de assistente via RoleManager."
            return
        }
        launchAssistantRolePicker()
    }

    private fun requestMicrophonePermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            status.text = "Microfone já autorizado. A voz offline estará disponível quando o Android tiver um modelo local compatível."
            return
        }
        @Suppress("DEPRECATION")
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_ASSISTANT_VOICE)
    }

    private fun launchAssistantRolePicker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val manager = getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            status.text = "Este Android não disponibilizou a função de assistente padrão. O HUD e os comandos locais continuam disponíveis."
            return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            status.text = "J.A.R.V.I.S. já é o assistente padrão. Use o gesto de assistente definido nas configurações do telefone."
            return
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQUEST_ASSISTANT_ROLE)
            status.text = "Confirme na janela do Android para escolher J.A.R.V.I.S. como assistente."
        } catch (e: Exception) {
            status.text = "Não consegui abrir a seleção de assistente: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    @Deprecated("Uses Android role request result")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ASSISTANT_VOICE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                if (assistantInvocation) startAssistantVoiceInput()
                else status.text = "Microfone permitido. Se houver reconhecimento offline instalado, a voz poderá ser usada."
            } else {
                status.text = "Microfone negado; você ainda pode usar comandos digitados."
            }
        }
    }

    @Deprecated("Uses Android role request result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ASSISTANT_ROLE) {
            status.text = if (resultCode == RESULT_OK) {
                "J.A.R.V.I.S. foi escolhido como assistente. Configure o gesto de invocação nas opções do Android."
            } else {
                "A escolha do assistente foi cancelada. Você pode tentar novamente."
            }
        }
    }

    private fun launchHud() {
        if (hudLaunched || !Settings.canDrawOverlays(this)) return
        hudLaunched = true
        val intent = Intent(this, HudOverlayService::class.java).putExtra("open_panel", true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            finish()
        } catch (e: Exception) {
            hudLaunched = false
            if (::status.isInitialized) status.text = "Não foi possível iniciar o HUD: " + (e.message ?: e.javaClass.simpleName)
            Toast.makeText(this, "O Android impediu a inicialização. Confira as permissões.", Toast.LENGTH_LONG).show()
        }
    }

    private fun addButton(parent: LinearLayout, title: String, primary: Boolean, action: () -> Unit) {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            minHeight = dp(48)
            setTextColor(if (primary) Color.rgb(8, 23, 32) else Color.WHITE)
            background = rounded(if (primary) Color.rgb(91, 224, 238) else Color.rgb(34, 51, 65))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
    }

    private fun rounded(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(14).toFloat()
        if (fill != Color.rgb(91, 224, 238)) setStroke(dp(1), Color.rgb(56, 86, 104))
    }

    override fun onDestroy() {
        releaseAssistantRecognizer()
        super.onDestroy()
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_CONFIGURE_ASSISTANT = "configure_jarvis_assistant"
        private const val REQUEST_ASSISTANT_ROLE = 6801
        private const val REQUEST_ASSISTANT_VOICE = 6803
    }
}
