package com.jarvis.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import java.util.Locale

class JarvisVoiceInteractionSession(private val host: Context) : VoiceInteractionSession(host) {
    private var input: EditText? = null
    private var status: TextView? = null
    private var orb: JarvisOrbView? = null
    private var listenButton: Button? = null
    private var executeButton: Button? = null
    private var loginButton: Button? = null
    private var recognizer: SpeechRecognizer? = null
    private var speaker: TextToSpeech? = null
    private var speakerReady = false
    private var pendingCommand: String? = null
    private var busySpeaking = false

    override fun onCreateContentView(): View {
        val page = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(12))
            background = rounded(Color.rgb(7, 10, 18), Color.rgb(43, 92, 127))
            elevation = dp(18).toFloat()
        }
        page.addView(TextView(host).apply {
            text = "J.A.R.V.I.S."
            textSize = 22f
            setTextColor(Color.rgb(229, 245, 255))
        })
        page.addView(TextView(host).apply {
            text = "ASSISTENTE DO SISTEMA"
            textSize = 8f
            letterSpacing = 0.17f
            setTextColor(Color.rgb(75, 210, 255))
            setPadding(0, dp(3), 0, dp(10))
        })
        status = TextView(host).apply {
            text = "Preparando o assistente…"
            textSize = 13f
            setTextColor(Color.rgb(229, 238, 247))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(17, 22, 33), Color.rgb(37, 66, 88))
            minHeight = dp(52)
        }
        page.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        input = EditText(host).apply {
            hint = "Transcrição ou comando"
            setHintTextColor(Color.rgb(118, 142, 165))
            setTextColor(Color.WHITE)
            textSize = 14f
            minLines = 1
            maxLines = 3
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(Color.rgb(15, 25, 40), Color.rgb(40, 83, 112))
        }
        page.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        executeButton = addButton(page, "Confirmar e executar") {
            executeCommand(pendingCommand ?: input?.text?.toString().orEmpty())
        }
        listenButton = addButton(page, "Tentar ouvir novamente") { startListening() }
        loginButton = addButton(page, "Abrir login do J.A.R.V.I.S.") {
            host.startActivity(Intent(host, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        addButton(page, "Cancelar / limpar comando") {
            pendingCommand = null
            input?.setText("")
            status?.text = "Comando cancelado. Nenhuma ação foi executada."
            orb?.mode = OrbMode.IDLE
            executeButton?.isEnabled = false
        }
        loginButton?.visibility = if (JarvisAuthState.hasAccess(host)) View.GONE else View.VISIBLE
        page.addView(Space(host), LinearLayout.LayoutParams(1, 0, 1f))
        orb = JarvisOrbView(host).apply {
            contentDescription = "Esfera de voz do J.A.R.V.I.S."
            setOnClickListener { startListening() }
        }
        page.addView(orb, LinearLayout.LayoutParams(dp(96), dp(96)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(3)
        })
        page.addView(TextView(host).apply {
            text = "Comandos ambíguos exigem confirmação antes da execução."
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(113, 151, 181))
            setPadding(0, dp(5), 0, 0)
        })
        initializeSpeaker()
        return page
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (!JarvisAuthState.hasAccess(host)) {
            status?.text = "Entre no J.A.R.V.I.S. primeiro. Nenhum comando será executado antes da configuração inicial."
            loginButton?.visibility = View.VISIBLE
            executeButton?.isEnabled = false
            listenButton?.isEnabled = false
            return
        }
        status?.text = "Fale uma instrução. Vou mostrar o que entendi antes de executar."
        loginButton?.visibility = View.GONE
        executeButton?.isEnabled = false
        listenButton?.isEnabled = true
        startListening()
    }

    override fun onHide() {
        stopListening()
        stopSpeaker()
        super.onHide()
    }

    override fun onDestroy() {
        stopListening()
        stopSpeaker()
        try { speaker?.shutdown() } catch (_: Exception) {}
        speaker = null
        super.onDestroy()
    }

    private fun receiveTranscript(text: String, confidence: Float?) {
        val transcript = JarvisCommandInterpreter.normalizeTranscript(text)
        if (transcript.isBlank()) {
            status?.text = "Não recebi uma transcrição legível. Tente falar mais devagar."
            orb?.mode = OrbMode.IDLE
            return
        }
        if (confidence != null && confidence in 0f..1f && confidence < 0.42f) {
            status?.text = "Baixa confiança na transcrição. Ouvi: " + text + ". Não vou executar; repita ou edite."
            input?.setText(text)
            pendingCommand = null
            executeButton?.isEnabled = false
            orb?.mode = OrbMode.IDLE
            speak(status?.text.toString(), false)
            return
        }
        val pending = pendingCommand
        if (pending != null) {
            when (transcript) {
                "confirmar", "confirma", "pode executar", "executar", "isso mesmo", "correto" -> {
                    executeCommand(pending)
                    return
                }
                "cancelar", "cancele", "nao", "não", "pare", "parar" -> {
                    pendingCommand = null
                    input?.setText("")
                    executeButton?.isEnabled = false
                    status?.text = "Comando cancelado. Nenhuma ação foi executada."
                    speak("Comando cancelado.", false)
                    return
                }
            }
        }
        val intent = JarvisCommandInterpreter.classify(text)
        if (intent.kind == JarvisIntentKind.UNKNOWN) {
            pendingCommand = null
            input?.setText(text)
            executeButton?.isEnabled = false
            status?.text = "Não reconheci isso como um comando suportado. Nada foi executado. Reformule ou diga “pesquise na Web ...”."
            orb?.mode = OrbMode.IDLE
            speak("Não reconheci essa instrução com segurança. Não executei nenhuma ação.", false)
            return
        }
        pendingCommand = text.trim()
        input?.setText(text.trim())
        input?.setSelection(input?.text?.length ?: 0)
        executeButton?.isEnabled = true
        status?.text = "Entendi: " + text.trim() + ". Diga confirmar para executar ou cancelar para parar."
        speak("Entendi: " + text.trim() + ". Diga confirmar para executar ou cancelar para parar.", true)
    }

    private fun executeCommand(text: String) {
        if (text.isBlank()) {
            status?.text = "Fale uma instrução ou digite no campo."
            return
        }
        val intent = JarvisCommandInterpreter.classify(text)
        if (intent.kind == JarvisIntentKind.UNKNOWN) {
            status?.text = "Não reconheci essa instrução. Nenhuma ação foi executada."
            pendingCommand = null
            executeButton?.isEnabled = false
            orb?.mode = OrbMode.IDLE
            return
        }
        pendingCommand = null
        executeButton?.isEnabled = false
        stopListening()
        orb?.mode = OrbMode.PROCESSING
        val result = try {
            JarvisCommandRouter.execute(host, text)
        } catch (e: Exception) {
            "Não consegui concluir a ação: " + (e.message ?: e.javaClass.simpleName)
        }
        status?.text = result
        LocalAutomationEngine.setStatus(host, result)
        speak(result, false)
    }

    private fun initializeSpeaker() {
        speaker = TextToSpeech(host) { result ->
            if (result != TextToSpeech.SUCCESS) return@TextToSpeech
            val tts = speaker ?: return@TextToSpeech
            speakerReady = true
            tts.language = Locale("pt", "BR")
            tts.setSpeechRate(0.92f)
            tts.setPitch(0.96f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val voice = tts.voices
                    ?.filter { it.locale.language == "pt" && !it.isNetworkConnectionRequired }
                    ?.maxByOrNull { it.quality }
                if (voice != null) tts.voice = voice
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        orb?.post { busySpeaking = true; orb?.mode = OrbMode.SPEAKING }
                    }
                    override fun onDone(utteranceId: String?) {
                        orb?.post {
                            busySpeaking = false
                            orb?.mode = OrbMode.IDLE
                            if (pendingCommand != null && JarvisAuthState.hasAccess(host)) startListening()
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        orb?.post { busySpeaking = false; orb?.mode = OrbMode.IDLE }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        orb?.post { busySpeaking = false; orb?.mode = OrbMode.IDLE }
                    }
                })
            }
        }
    }

    private fun speak(text: String, listenAfter: Boolean) {
        if (!speakerReady || text.isBlank()) {
            orb?.mode = OrbMode.IDLE
            if (listenAfter && pendingCommand != null) startListening()
            return
        }
        busySpeaking = true
        orb?.mode = OrbMode.SPEAKING
        speaker?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis-global-" + System.currentTimeMillis())
    }

    private fun startListening() {
        if (!JarvisAuthState.hasAccess(host)) {
            status?.text = "Entre no app antes de usar o assistente global."
            loginButton?.visibility = View.VISIBLE
            return
        }
        if (busySpeaking) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            status?.text = "O reconhecimento offline exige Android 12 ou superior. Você pode digitar o comando."
            orb?.mode = OrbMode.IDLE
            return
        }
        if (host.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status?.text = "Permita o microfone nas informações do app. A transcrição digitada continua disponível."
            orb?.mode = OrbMode.IDLE
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(host)) {
            status?.text = "Não há modelo de fala offline instalado. Instale o pacote Português (Brasil) do reconhecedor ou digite."
            orb?.mode = OrbMode.IDLE
            return
        }
        stopListening()
        try {
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(host)
            recognizer = speech
            listenButton?.text = "Ouvindo…"
            orb?.mode = OrbMode.LISTENING
            status?.text = if (pendingCommand == null) "Estou ouvindo uma instrução…" else "Aguardando confirmar ou cancelar…"
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { orb?.mode = OrbMode.LISTENING }
                override fun onBeginningOfSpeech() { orb?.mode = OrbMode.LISTENING }
                override fun onRmsChanged(rmsdB: Float) { orb?.setAudioLevel(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status?.text = "Entendendo a fala…" }
                override fun onError(error: Int) {
                    releaseRecognizer()
                    listenButton?.text = "Tentar ouvir novamente"
                    status?.text = if (pendingCommand == null) {
                        "O reconhecimento falhou (código " + error + "). Tente novamente ou digite."
                    } else {
                        "Comando preparado: toque em Confirmar e executar ou tente falar confirmar."
                    }
                    orb?.mode = OrbMode.IDLE
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    val score = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull()
                    releaseRecognizer()
                    listenButton?.text = "Tentar ouvir novamente"
                    receiveTranscript(spoken, score)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) status?.text = partial
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
            releaseRecognizer()
            orb?.mode = OrbMode.IDLE
            status?.text = "Não consegui iniciar a voz offline: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun stopListening() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
        releaseRecognizer()
        listenButton?.text = "Tentar ouvir novamente"
        if (!busySpeaking) orb?.mode = OrbMode.IDLE
    }

    private fun releaseRecognizer() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun stopSpeaker() {
        busySpeaking = false
        try { speaker?.stop() } catch (_: Exception) {}
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(host).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            minHeight = dp(42)
            setTextColor(Color.rgb(230, 242, 252))
            background = rounded(Color.rgb(19, 31, 47), Color.rgb(45, 87, 116))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(4) })
        return button
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(15).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * host.resources.displayMetrics.density).toInt()
}
