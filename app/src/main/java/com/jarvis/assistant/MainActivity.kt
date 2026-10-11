package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var transcript: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var prompt: EditText
    private lateinit var status: TextView
    private lateinit var micOrb: JarvisOrbView
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var assistantInvocation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 10, 18)
        window.navigationBarColor = Color.rgb(7, 10, 18)
        if (!JarvisAuthState.hasAccess(this)) {
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
            return
        }
        assistantInvocation = intent.action == Intent.ACTION_ASSIST
        render()
        renderConversation()
        if (intent.getBooleanExtra(EXTRA_CONFIGURE_ASSISTANT, false)) window.decorView.post { requestAssistantRole() }
        if (assistantInvocation) window.decorView.post { startVoiceInput() }
    }

    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 10, 18))
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(1), dp(5), dp(1), dp(7))
        }
        header.addView(chip("☰", Color.rgb(22, 25, 34), Color.rgb(226, 236, 245)) {
            showConversationDrawer()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        title.addView(TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 18f
            setTextColor(Color.rgb(238, 245, 252))
            setTypeface(typeface, Typeface.BOLD)
        })
        title.addView(TextView(this).apply {
            text = if (JarvisAuthState.isOwner(this@MainActivity)) "PROPRIETÁRIO GLOBAL" else "ASSISTENTE PESSOAL"
            textSize = 8f
            letterSpacing = 0.14f
            setTextColor(if (JarvisAuthState.isOwner(this@MainActivity)) Color.rgb(100, 225, 170) else Color.rgb(73, 203, 250))
        })
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(chip("+", Color.rgb(22, 25, 34), Color.rgb(226, 236, 245)) {
            createConversation()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(header)

        status = TextView(this).apply {
            text = if (assistantInvocation) "Assistente do sistema · revise o comando antes de executar" else "Suas conversas são salvas neste telefone."
            textSize = 11f
            setTextColor(Color.rgb(119, 151, 174))
            setPadding(dp(7), dp(3), dp(7), dp(6))
        }
        root.addView(status)

        scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(dp(2), dp(3), dp(2), dp(5))
        }
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(transcript, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val shortcutRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, dp(7))
        }
        shortcutRow.addView(chip("Pesquisar Web", Color.rgb(17, 30, 46), Color.rgb(180, 225, 250)) {
            prompt.setText("pesquise na Web ")
            prompt.setSelection(prompt.text.length)
            prompt.requestFocus()
        }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { rightMargin = dp(4) })
        shortcutRow.addView(chip("Assistente", Color.rgb(17, 30, 46), Color.rgb(180, 225, 250)) {
            requestAssistantRole()
        }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { leftMargin = dp(4) })
        root.addView(shortcutRow)

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(23, 25, 33), Color.rgb(54, 63, 78), 25)
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        prompt = EditText(this).apply {
            hint = "Perguntar ao J.A.R.V.I.S."
            setHintTextColor(Color.rgb(129, 139, 154))
            setTextColor(Color.rgb(243, 246, 250))
            textSize = 14f
            minLines = 1
            maxLines = 4
            gravity = Gravity.CENTER_VERTICAL
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(10), dp(8), dp(6), dp(8))
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { submitCommand(); true } else false
            }
        }
        composer.addView(prompt, LinearLayout.LayoutParams(0, -2, 1f))
        micOrb = JarvisOrbView(this).apply {
            contentDescription = "Falar com J.A.R.V.I.S."
            setOnClickListener { startVoiceInput() }
        }
        composer.addView(micOrb, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(4) })
        composer.addView(chip("↑", Color.rgb(54, 191, 246), Color.rgb(6, 19, 31)) {
            submitCommand()
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(composer)
        SystemBarInsets.apply(root)
        setContentView(root)
    }

    private fun renderConversation() {
        transcript.removeAllViews()
        val conversation = JarvisConversationStore.current(this)
        val lines = conversation?.lines.orEmpty()
        if (lines.isEmpty()) {
            addMessage("Olá. Sou J.A.R.V.I.S. Posso executar comandos locais, abrir aplicativos, calcular, guardar notas e pesquisar na Web. Para reduzir erros de voz, comandos falados precisam ser revisados antes de serem enviados.", false)
            addMessage("Escreva uma instrução ou toque na pequena esfera ao lado do campo. Perguntas que exigem informação atual podem ser consultadas na Web.", false)
        } else {
            lines.forEach { addMessage(it.text, it.role == "user") }
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addMessage(text: String, fromUser: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (fromUser) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(if (fromUser) Color.rgb(242, 247, 252) else Color.rgb(222, 229, 237))
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = rounded(
                if (fromUser) Color.rgb(29, 47, 66) else Color.rgb(17, 20, 28),
                if (fromUser) Color.rgb(52, 89, 117) else Color.rgb(37, 42, 53),
                17
            )
        }
        row.addView(bubble, LinearLayout.LayoutParams(-1, -2).apply {
            if (fromUser) leftMargin = dp(38) else rightMargin = dp(25)
        })
        transcript.addView(row)
    }

    private fun submitCommand() {
        val command = prompt.text?.toString()?.trim().orEmpty()
        if (command.isBlank()) {
            status.text = "Escreva uma instrução ou toque na esfera para falar."
            return
        }
        prompt.text?.clear()
        status.text = "Interpretando a instrução…"
        val intent = JarvisCommandInterpreter.classify(command)
        val answer = when {
            isGreeting(intent.cleanedText) -> "Olá. Estou pronto para ajudar. Posso executar comandos locais disponíveis ou pesquisar informações na Web."
            intent.kind == SafeCommandKind.UNKNOWN ->
                "Não consegui interpretar essa frase como um comando seguro, então não executei nada. Reformule o pedido ou use “Pesquisar Web” para uma consulta explícita."
            else -> try { JarvisCommandRouter.execute(this, command) }
            catch (e: Exception) { "Não consegui concluir a ação: " + (e.message ?: e.javaClass.simpleName) }
        }
        JarvisConversationStore.appendExchange(this, command, answer)
        addMessage(command, true)
        addMessage(answer, false)
        status.text = if (JarvisAuthState.isGoogle(this)) "Conta: " + JarvisAuthState.currentAccount(this).email else "Modo local · nenhum proprietário global"
        LocalAutomationEngine.setStatus(this, answer)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun isGreeting(text: String): Boolean =
        text in setOf("oi", "ola", "bom dia", "boa tarde", "boa noite", "obrigado", "obrigada", "valeu")

    private fun createConversation() {
        JarvisConversationStore.create(this)
        renderConversation()
        prompt.setText("")
        status.text = "Nova conversa."
    }

    private fun showConversationDrawer() {
        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(14), dp(14))
            setBackgroundColor(Color.rgb(20, 21, 25))
        }
        panel.addView(TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = "Suas conversas"
            textSize = 12f
            setTextColor(Color.rgb(145, 153, 166))
            setPadding(0, dp(4), 0, dp(14))
        })
        panel.addView(chip("＋  Nova conversa", Color.rgb(45, 47, 54), Color.WHITE) {
            dialog.dismiss()
            createConversation()
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(13) })
        val historyScroll = ScrollView(this)
        val history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyScroll.addView(history, ViewGroup.LayoutParams(-1, -2))
        JarvisConversationStore.all(this).take(35).forEach { chat ->
            history.addView(TextView(this).apply {
                text = chat.title
                textSize = 14f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(if (chat.id == JarvisConversationStore.currentId(this@MainActivity)) Color.WHITE else Color.rgb(193, 198, 207))
                setPadding(dp(11), dp(12), dp(11), dp(12))
                background = rounded(
                    if (chat.id == JarvisConversationStore.currentId(this@MainActivity)) Color.rgb(43, 45, 52) else Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    11
                )
                setOnClickListener {
                    JarvisConversationStore.select(this@MainActivity, chat.id)
                    dialog.dismiss()
                    renderConversation()
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
        }
        panel.addView(historyScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val account = JarvisAuthState.currentAccount(this)
        panel.addView(View(this).apply { setBackgroundColor(Color.rgb(54, 56, 63)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) })
        panel.addView(TextView(this).apply {
            text = account.displayName.ifBlank { "Modo local" }
            textSize = 14f
            setTextColor(Color.WHITE)
        })
        panel.addView(TextView(this).apply {
            text = when {
                account.isOwner -> "Proprietário global"
                JarvisAuthState.isGoogle(this@MainActivity) -> account.email
                else -> "Sem conta · dados neste dispositivo"
            }
            textSize = 11f
            setTextColor(if (account.isOwner) Color.rgb(98, 222, 168) else Color.rgb(137, 149, 165))
            setPadding(0, dp(3), 0, dp(10))
        })
        panel.addView(chip("Configurações", Color.rgb(37, 39, 46), Color.WHITE) {
            dialog.dismiss()
            showSettings()
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(5) })
        panel.addView(chip("Sair da conta / modo local", Color.rgb(37, 39, 46), Color.rgb(244, 189, 189)) {
            dialog.dismiss()
            signOut()
        }, LinearLayout.LayoutParams(-1, dp(42)))
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(true)
        dialog.show()
        dialog.window?.setLayout(dp(305), ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setGravity(Gravity.START)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.attributes = dialog.window?.attributes?.apply { dimAmount = 0.48f }
    }

    private fun showSettings() {
        val options = arrayOf(
            "Definir como assistente padrão",
            "Configurar acessibilidade",
            "Configurar esfera flutuante",
            "Limpar conversas locais",
            "Informações sobre a inteligência"
        )
        AlertDialog.Builder(this).setTitle("Configurações do J.A.R.V.I.S.").setItems(options) { _, which ->
            when (which) {
                0 -> requestAssistantRole()
                1 -> try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
                2 -> openOverlaySettings()
                3 -> AlertDialog.Builder(this).setTitle("Limpar histórico")
                    .setMessage("Isso apagará as conversas salvas neste telefone.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Apagar") { _, _ ->
                        JarvisConversationStore.clearAll(this)
                        JarvisConversationStore.create(this)
                        renderConversation()
                    }.show()
                4 -> AlertDialog.Builder(this).setTitle("Como a inteligência funciona")
                    .setMessage("O núcleo local é um classificador de intenções com regras, memória de comandos e automação Android; não é um modelo generativo equivalente a sistemas de ponta. Pedidos ambíguos não são executados automaticamente.")
                    .setPositiveButton("Entendi", null).show()
            }
        }.setNegativeButton("Fechar", null).show()
    }

    private fun openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            try {
                val hud = Intent(this, HudOverlayService::class.java).putExtra("open_panel", true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(hud) else startService(hud)
                status.text = "Esfera flutuante iniciada."
            } catch (e: Exception) {
                status.text = "Não consegui iniciar a esfera: " + (e.message ?: e.javaClass.simpleName)
            }
        } else {
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))) }
            catch (_: Exception) { status.text = "Ative Permitir sobreposição nas configurações do Android." }
        }
    }

    private fun requestAssistantRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            status.text = "Esta versão do Android não oferece a seleção via RoleManager."
            return
        }
        val manager = getSystemService(RoleManager::class.java)
        if (manager == null || !manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            status.text = "O Android não disponibilizou a função de assistente padrão."
            return
        }
        if (manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            status.text = "J.A.R.V.I.S. já é o assistente padrão."
            return
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQUEST_ASSISTANT_ROLE)
        } catch (e: Exception) {
            status.text = "Não consegui abrir a seleção: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun startVoiceInput() {
        if (listening) {
            stopVoiceInput()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            status.text = "Permita o microfone para usar a voz. O texto continua disponível."
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            status.text = "Não há modelo de reconhecimento offline compatível instalado. Use texto ou instale o pacote de fala offline."
            micOrb.mode = OrbMode.IDLE
            return
        }
        releaseRecognizer()
        try {
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer = speech
            listening = true
            micOrb.mode = OrbMode.LISTENING
            status.text = "Estou ouvindo…"
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.text = "Estou ouvindo…"; micOrb.mode = OrbMode.LISTENING }
                override fun onBeginningOfSpeech() { micOrb.mode = OrbMode.LISTENING }
                override fun onRmsChanged(rmsdB: Float) { micOrb.setAudioLevel(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.text = "Reconhecendo sua fala…" }
                override fun onError(error: Int) {
                    listening = false
                    releaseRecognizer()
                    micOrb.mode = OrbMode.IDLE
                    status.text = "O reconhecimento falhou (código " + error + "). Tente novamente ou escreva."
                }
                override fun onResults(results: Bundle?) {
                    val transcriptText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    val confidence = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
                    listening = false
                    releaseRecognizer()
                    micOrb.mode = OrbMode.IDLE
                    if (transcriptText.isNotBlank() && (confidence == null || confidence < 0f || confidence >= 0.48f)) {
                        prompt.setText(transcriptText)
                        prompt.setSelection(prompt.text.length)
                        status.text = "Transcrição preparada. Revise e pressione enviar; nada foi executado."
                    } else {
                        status.text = "Não consegui reconhecer com confiança suficiente. Repita mais devagar."
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
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
            releaseRecognizer()
            micOrb.mode = OrbMode.IDLE
            status.text = "Não consegui iniciar o reconhecimento local: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun stopVoiceInput() {
        listening = false
        try { recognizer?.stopListening() } catch (_: Exception) {}
        releaseRecognizer()
        if (::micOrb.isInitialized) micOrb.mode = OrbMode.IDLE
    }

    private fun releaseRecognizer() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun signOut() {
        AlertDialog.Builder(this).setTitle("Sair")
            .setMessage("Você voltará à tela de login. As conversas locais permanecerão neste telefone.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sair") { _, _ ->
                JarvisAuthState.signOut(this)
                startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }.show()
    }

    @Deprecated("Android role request result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ASSISTANT_ROLE) {
            status.text = if (resultCode == RESULT_OK) "Seleção aceita pelo Android. Teste o gesto de assistente." else "Seleção cancelada."
        }
    }

    @Deprecated("Android permission callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startVoiceInput()
            else status.text = "Microfone negado; use texto."
        }
    }

    private fun chip(label: String, fill: Int, textColor: Int, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(fill, if (fill == Color.TRANSPARENT) Color.TRANSPARENT else Color.rgb(47, 57, 72), 15)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        stopVoiceInput()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CONFIGURE_ASSISTANT = "configure_jarvis_assistant"
        private const val REQUEST_ASSISTANT_ROLE = 8801
        private const val REQUEST_MIC = 8802
    }
}
