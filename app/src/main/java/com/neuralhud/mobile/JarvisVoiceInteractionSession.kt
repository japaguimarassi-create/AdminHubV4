package com.neuralhud.mobile

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
import android.service.voice.VoiceInteractionSession
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class JarvisVoiceInteractionSession(private val host: Context) : VoiceInteractionSession(host) {
    private var input: EditText? = null
    private var status: TextView? = null
    private var listenButton: Button? = null
    private var recognizer: SpeechRecognizer? = null

    override fun onCreateContentView(): View {
        val page = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(Color.rgb(13, 22, 33), Color.rgb(68, 117, 138))
            elevation = dp(16).toFloat()
        }

        page.addView(TextView(host).apply {
            text = "J.A.R.V.I.S."
            textSize = 23f
            setTextColor(Color.rgb(91, 224, 238))
        })
        page.addView(TextView(host).apply {
            text = "Núcleo local • 2.678 neurônios • sem API de IA"
            textSize = 12f
            setTextColor(Color.rgb(174, 194, 207))
            setPadding(0, dp(3), 0, dp(12))
        })

        status = TextView(host).apply {
            text = "Assistente global ativado. Preparando reconhecimento de voz offline…"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Color.rgb(20, 32, 43), Color.rgb(37, 65, 80))
            minHeight = dp(52)
        }
        page.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        input = EditText(host).apply {
            hint = "Fale ou digite qualquer instrução"
            setHintTextColor(Color.rgb(145, 167, 181))
            setTextColor(Color.WHITE)
            textSize = 15f
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.rgb(23, 36, 48), Color.rgb(44, 78, 95))
        }
        page.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        listenButton = addButton(page, "Ouvir offline") { startListening() }
        addButton(page, "Executar comando") {
            val text = input?.text?.toString().orEmpty()
            val result = JarvisCommandRouter.execute(host, text)
            status?.text = result
            LocalAutomationEngine.setStatus(host, result)
        }
        addButton(page, "Fechar assistente") {
            stopListening()
            hide()
        }

        val footer = TextView(host).apply {
            text = "O reconhecimento por voz só funciona offline quando o Android disponibiliza um modelo local. Perguntas que exigem fatos recentes podem abrir o navegador."
            textSize = 11f
            setTextColor(Color.rgb(145, 167, 181))
            setPadding(0, dp(10), 0, 0)
        }
        page.addView(footer)
        return page
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        status?.text = "J.A.R.V.I.S. pronto. Diga o comando ou digite no campo."
        startListening()
    }

    override fun onHide() {
        stopListening()
        super.onHide()
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    private fun startListening() {
        val field = input ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            status?.text = "Este Android não oferece a opção local segura usada por J.A.R.V.I.S. Digite o comando ou use o microfone do teclado com cautela."
            return
        }
        if (host.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status?.text = "Permita o microfone nas informações do aplicativo. Você ainda pode digitar o comando."
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(host)) {
            status?.text = "Não há reconhecedor offline instalado para este idioma/dispositivo. Digite a instrução; nenhuma API de IA será chamada."
            return
        }
        stopListening()
        try {
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(host)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status?.text = "Estou ouvindo. Fale o comando; a gravação termina quando você pausar."
                    listenButton?.text = "Ouvindo…"
                }
                override fun onBeginningOfSpeech() {
                    status?.text = "Reconhecendo fala no dispositivo…"
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    status?.text = "Processando fala no dispositivo…"
                }
                override fun onError(error: Int) {
                    listenButton?.text = "Tentar ouvir novamente"
                    status?.text = "O reconhecimento offline falhou (código $error). Você pode tentar de novo ou digitar o comando."
                    releaseRecognizer()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull().orEmpty()
                    if (transcript.isNotBlank()) {
                        field.setText(transcript)
                        field.setSelection(field.text.length)
                        status?.text = "Comando reconhecido localmente. Revise e toque em “Executar comando”."
                    } else {
                        status?.text = "Não consegui entender a fala. Tente novamente ou digite."
                    }
                    listenButton?.text = "Ouvir offline"
                    releaseRecognizer()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val transcript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!transcript.isNullOrBlank()) status?.text = transcript
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speech.startListening(intent)
        } catch (e: Exception) {
            releaseRecognizer()
            status?.text = "Não consegui iniciar o reconhecedor offline: ${e.message ?: e.javaClass.simpleName}. Digite o comando."
        }
    }

    private fun stopListening() {
        try { recognizer?.stopListening() } catch (_: Exception) {}
        releaseRecognizer()
        listenButton?.text = "Ouvir offline"
    }

    private fun releaseRecognizer() {
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit): Button {
        val button = Button(host).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            minHeight = dp(46)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(34, 51, 65), Color.rgb(56, 86, 104))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(5) })
        return button
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * host.resources.displayMetrics.density).toInt()
}
