package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var transcript: LinearLayout
    private lateinit var transcriptScroll: ScrollView
    private lateinit var prompt: EditText
    private lateinit var statusLine: TextView
    private lateinit var orb: JarvisOrbView
    private var recognizer: SpeechRecognizer? = null
    private var speaker: TextToSpeech? = null
    private var speakerReady = false
    private var assistantInvocation = false
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 12, 22)
        window.navigationBarColor = Color.rgb(7, 12, 22)
        assistantInvocation = intent.action == Intent.ACTION_ASSIST
        JarvisLocalBrain.prewarm(this)
        renderMain()
        initializeSpeaker()
        if (assistantInvocation) window.decorView.post { startVoiceInput() }
    }

    private fun renderMain() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 12, 22))
            setPadding(dp(17), dp(7), dp(17), dp(9))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(7), 0, dp(10))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = "Símbolo do J.A.R.V.I.S."
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titles.addView(TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 21f
            setTextColor(Color.rgb(236, 248, 255))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.035f
        })
        titles.addView(TextView(this).apply {
            text = "PERSONAL INTELLIGENCE SYSTEM"
            textSize = 8f
            letterSpacing = 0.13f
            setTextColor(Color.rgb(69, 205, 255))
        })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(actionChip("⋮", Color.rgb(16, 30, 47), Color.rgb(196, 229, 248)) { showSettings() },
            LinearLayout.LayoutParams(dp(43), dp(43)))
        root.addView(header)

        val statusPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.rgb(12, 22, 37), Color.rgb(30, 69, 99), 17)
        }
        statusPanel.addView(TextView(this).apply {
            text = if (assistantInvocation) "ASSISTANT SESSION" else "LOCAL CORE  /  READY"
            textSize = 8f
            letterSpacing = 0.17f
            setTextColor(Color.rgb(74, 218, 255))
        })
        statusLine = TextView(this).apply {
            text = if (assistantInvocation) "Estou aqui. O que você precisa?" else "Comandos locais, memória e pesquisa Web dentro do app."
            textSize = 13f
            setTextColor(Color.rgb(211, 230, 244))
            setPadding(0, dp(4), 0, 0)
        }
        statusPanel.addView(statusLine)
        root.addView(statusPanel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })

        transcriptScroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, dp(2), 0, dp(2))
        }
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        transcriptScroll.addView(transcript, android.view.ViewGroup.LayoutParams(-1, -2))
        root.addView(transcriptScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addMessage("Olá. Sou J.A.R.V.I.S. Posso executar comandos no telefone, abrir aplicativos, calcular, guardar notas e pesquisar na Web. Para controlar outros apps, o Android exige que você ative Acessibilidade manualmente.", false)

        val orbArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(1), 0, dp(4))
        }
        orb = JarvisOrbView(this).apply {
            contentDescription = "Esfera de voz do J.A.R.V.I.S. Toque para falar."
            setOnClickListener { startVoiceInput() }
        }
        orbArea.addView(orb, LinearLayout.LayoutParams(dp(118), dp(118)))
        orbArea.addView(TextView(this).apply {
            text = "TOQUE NA ESFERA PARA FALAR"
            textSize = 8f
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(93, 170, 209))
            setPadding(0, 0, 0, dp(3))
        })
        root.addView(orbArea, LinearLayout.LayoutParams(-1, -2))

        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        quick.addView(actionChip("Pesquisar Web", Color.rgb(13, 35, 54), Color.rgb(162, 225, 255)) {
            prompt.setText("pesquise ")
            prompt.setSelection(prompt.text.length)
            prompt.requestFocus()
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(4) })
        quick.addView(actionChip("Assistente padrão", Color.rgb(13, 35, 54), Color.rgb(162, 225, 255)) {
            requestAssistantRole()
        }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(4) })
        root.addView(quick, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(13, 25, 41), Color.rgb(34, 82, 113), 22)
            setPadding(dp(7), dp(4), dp(7), dp(4))
        }
        prompt = EditText(this).apply {
            hint = "Fale com o J.A.R.V.I.S."
            setHintTextColor(Color.rgb(102, 132, 153))
            setTextColor(Color.rgb(235, 246, 255))
            textSize = 14f
            minLines = 1
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            imeOptions = EditorInfo.IME_ACTION_SEND
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { submitCommand(); true } else false
            }
        }
        composer.addView(prompt, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(actionChip("↑", Color.rgb(50, 199, 255), Color.rgb(4, 18, 32)) {
            submitCommand()
        }, LinearLayout.LayoutParams(dp(43), dp(43)))
        root.addView(composer)
        SystemBarInsets.apply(root)
        setContentView(root)
    }

    private fun addMessage(message: String, fromUser: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (fromUser) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }
        val bubble = TextView(this).apply {
            text = message
            textSize = 13.5f
            setTextColor(if (fromUser) Color.rgb(241, 250, 255) else Color.rgb(213, 229, 242))
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = rounded(if (fromUser) Color.rgb(18, 52, 78) else Color.rgb(12, 23, 38),
                if (fromUser) Color.rgb(37, 116, 163) else Color.rgb(29, 62, 86), 15)
        }
        row.addView(bubble, LinearLayout.LayoutParams(-1, -2).apply {
            if (fromUser) leftMargin = dp(32) else rightMargin = dp(18)
        })
        transcript.addView(row)
        transcriptScroll.post { transcriptScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun submitCommand() {
        val command = prompt.text?.toString()?.trim().orEmpty()
        if (command.isBlank()) {
            statusLine.text = "Digite uma instrução ou toque na esfera."
            return
        }
        prompt.text?.clear()
        addMessage(command, true)
        statusLine.text = "Interpretando no dispositivo…"
        orb.mode = OrbMode.PROCESSING
        val result = try {
            JarvisCommandRouter.execute(this, command)
        } catch (e: Exception) {
            "Não consegui concluir a ação: " + (e.message ?: e.javaClass.simpleName)
        }
        LocalAutomationEngine.setStatus(this, result)
        addMessage(result, false)
        statusLine.text = "Pronto."
        speak(result)
    }

    private fun initializeSpeaker() {
        speaker = TextToSpeech(this) { result ->
            if (result == TextToSpeech.SUCCESS) {
                val tts = speaker
                if (tts != null) {
                    speakerReady = true
                    tts.language = Locale("pt", "BR")
                    tts.setSpeechRate(0.91f)
                    tts.setPitch(0.94f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val voice = tts.voices?.filter { it.locale.language == "pt" && !it.isNetworkConnectionRequired }?.maxByOrNull { it.quality }
                        if (voice != null) tts.voice = voice
                        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                runOnUiThread {
                                    orb.mode = OrbMode.SPEAKING
                                    statusLine.text = "J.A.R.V.I.S. falando…"
                                }
                            }
                            override fun onDone(utteranceId: String?) {
                                runOnUiThread {
                                    orb.mode = OrbMode.IDLE
                                    statusLine.text = "Pronto para a próxima instrução."
                                }
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                runOnUiThread { orb.mode = OrbMode.IDLE }
                            }
                            override fun onError(utteranceId: String?, errorCode: Int) {
                                runOnUiThread { orb.mode = OrbMode.IDLE }
                            }
                        })
                    }
                }
            }
        }
    }

    private fun speak(text: String) {
        if (!speakerReady || text.isBlank()) {
            orb.mode = OrbMode.IDLE
            return
        }
        orb.mode = OrbMode.SPEAKING
        speaker?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-" + System.currentTimeMillis())
    }

    private fun startVoiceInput() {
        if (listening) {
            stopVoiceInput()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            statusLine.text = "Permita o microfone para usar a voz."
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            statusLine.text = "Não há reconhecedor de voz offline compatível instalado. Os comandos por texto continuam disponíveis."
            orb.mode = OrbMode.IDLE
            return
        }
        releaseRecognizer()
        try {
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer = speech
            listening = true
            orb.mode = OrbMode.LISTENING
            statusLine.text = "Estou ouvindo…"
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { statusLine.text = "Estou ouvindo…"; orb.mode = OrbMode.LISTENING }
                override fun onBeginningOfSpeech() { orb.mode = OrbMode.LISTENING }
                override fun onRmsChanged(rmsdB: Float) { orb.setAudioLevel(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { statusLine.text = "Entendendo a instrução…" }
                override fun onError(error: Int) {
                    listening = false
                    orb.mode = OrbMode.IDLE
                    statusLine.text = "Não reconheci a fala (código " + error + "). Tente de novo ou digite."
                    releaseRecognizer()
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    listening = false
                    releaseRecognizer()
                    if (text.isNotBlank()) {
                        prompt.setText(text)
                        submitCommand()
                    } else {
                        orb.mode = OrbMode.IDLE
                        statusLine.text = "Não entendi. Tente novamente."
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) statusLine.text = text
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            })
        } catch (e: Exception) {
            listening = false
            orb.mode = OrbMode.IDLE
            statusLine.text = "Não consegui iniciar a voz offline: " + (e.message ?: e.javaClass.simpleName)
            releaseRecognizer()
        }
    }

    private fun stopVoiceInput() {
        listening = false
        try { recognizer?.stopListening() } catch (_: Exception) {}
        releaseRecognizer()
        if (::orb.isInitialized && orb.mode == OrbMode.LISTENING) orb.mode = OrbMode.IDLE
    }

    private fun releaseRecognizer() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun showSettings() {
        val items = arrayOf("Definir como assistente padrão", "Configurar Acessibilidade", "Configurar esfera flutuante", "Ferramentas e arquivos", "Sobre privacidade e login")
        AlertDialog.Builder(this).setTitle("J.A.R.V.I.S.").setItems(items) { _, which ->
            when (which) {
                0 -> requestAssistantRole()
                1 -> try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
                2 -> openOverlaySettings()
                3 -> startActivity(Intent(this, ToolboxActivity::class.java))
                4 -> AlertDialog.Builder(this)
                    .setTitle("Conta e privacidade")
                    .setMessage("Comandos e notas são locais. A pesquisa Web envia a consulta ao mecanismo de busca. A voz depende do sintetizador instalado. Login Google e proprietário global exigem OAuth e um backend configurado; esta versão não simula autenticação nem um proprietário global.")
                    .setPositiveButton("Entendi", null)
                    .show()
            }
        }.setNegativeButton("Fechar", null).show()
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            statusLine.text = "Esta versão do Android não oferece seleção de assistente via RoleManager."
            return
        }
        val manager = getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            statusLine.text = "O Android não disponibilizou a função de assistente padrão. O app continua funcionando pelo ícone."
            return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            statusLine.text = "J.A.R.V.I.S. já é o assistente padrão. Configure o gesto nas opções do Android."
            return
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQUEST_ASSISTANT_ROLE)
            statusLine.text = "Confirme J.A.R.V.I.S. na janela do Android."
        } catch (e: Exception) {
            statusLine.text = "Não consegui abrir a seleção: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            try {
                val hud = Intent(this, HudOverlayService::class.java).putExtra("open_panel", true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(hud) else startService(hud)
                statusLine.text = "Esfera flutuante iniciada."
            } catch (e: Exception) {
                statusLine.text = "Falha ao iniciar a esfera: " + (e.message ?: e.javaClass.simpleName)
            }
        } else {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))) }
            catch (_: Exception) { statusLine.text = "Ative Permitir sobreposição nas configurações do Android." }
        }
    }

    @Deprecated("Android role request result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ASSISTANT_ROLE) {
            statusLine.text = if (resultCode == RESULT_OK) "O Android aceitou a seleção. Teste o gesto de assistente do telefone." else "Seleção cancelada. J.A.R.V.I.S. segue disponível pelo ícone."
            addMessage(statusLine.text.toString(), false)
        }
    }

    @Deprecated("Android permission callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoiceInput()
            else statusLine.text = "Microfone negado; use os comandos por texto."
        }
    }

    private fun actionChip(label: String, fill: Int, textColor: Int, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 11.5f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(dp(7), dp(5), dp(7), dp(5))
            background = rounded(fill, if (fill == Color.rgb(50, 199, 255)) fill else Color.rgb(30, 70, 96), 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        stopVoiceInput()
        try { speaker?.stop() } catch (_: Exception) {}
        try { speaker?.shutdown() } catch (_: Exception) {}
        speaker = null
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_ASSISTANT_ROLE = 7801
        private const val REQUEST_MIC = 7802
    }

    companion object {
        const val EXTRA_CONFIGURE_ASSISTANT = "configure_jarvis_assistant"
        private const val REQUEST_ASSISTANT_ROLE = 7801
        private const val REQUEST_MIC = 7802
    }
}
