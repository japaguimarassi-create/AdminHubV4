package com.jarvis.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LoginActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var googleButton: Button
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 10, 18)
        window.navigationBarColor = Color.rgb(7, 10, 18)
        if (JarvisAuthState.hasAccess(this)) {
            openAssistant()
            return
        }
        render()
    }

    private fun render() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            setBackgroundColor(Color.rgb(7, 10, 18))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(page, ViewGroup.LayoutParams(-1, -2))
        val emblem = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = "J.A.R.V.I.S."
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        page.addView(emblem, LinearLayout.LayoutParams(dp(104), dp(104)).apply { bottomMargin = dp(20) })
        page.addView(TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(236, 247, 255))
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.025f
        }, matchWrap())
        page.addView(TextView(this).apply {
            text = "SEU ASSISTENTE PESSOAL"
            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = 0.20f
            setTextColor(Color.rgb(64, 208, 255))
            setPadding(0, dp(7), 0, dp(28))
        }, matchWrap())

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(18), dp(17), dp(17))
            background = rounded(Color.rgb(13, 23, 38), Color.rgb(31, 73, 102), 22)
        }
        card.addView(TextView(this).apply {
            text = "Entre para começar"
            textSize = 21f
            setTextColor(Color.rgb(229, 242, 253))
            setTypeface(typeface, Typeface.BOLD)
        }, matchWrap())
        card.addView(TextView(this).apply {
            text = "Use sua Conta Google. O primeiro usuário registrado no backend poderá ser definido como proprietário global. Nenhuma conta recebe esse papel por decisão local do aplicativo."
            textSize = 13f
            setTextColor(Color.rgb(151, 175, 198))
            setPadding(0, dp(7), 0, dp(18))
        }, matchWrap())

        googleButton = Button(this).apply {
            text = "G   Continuar com Google"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.rgb(22, 30, 43))
            background = rounded(Color.WHITE, Color.WHITE, 14)
            minHeight = dp(52)
            setOnClickListener { beginGoogleSignIn() }
        }
        card.addView(googleButton, matchWrap())
        status = TextView(this).apply {
            text = when {
                BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank() ->
                    "Backend Supabase criado. Falta o Web Client ID do Google: configure JARVIS_GOOGLE_WEB_CLIENT_ID no GitHub e GOOGLE_WEB_CLIENT_ID nos segredos do Supabase."
                BuildConfig.JARVIS_AUTH_ENDPOINT.isBlank() ->
                    "Falta configurar o endpoint de autenticação do backend neste build."
                else ->
                    "Backend conectado. O login Google está configurado; a primeira conta confirmada pelo servidor poderá ser o proprietário global."
            }
            textSize = 12f
            setTextColor(Color.rgb(152, 177, 199))
            setPadding(0, dp(12), 0, dp(2))
        }
        card.addView(status, matchWrap())
        page.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        page.addView(TextView(this).apply {
            text = "OU"
            textSize = 10f
            letterSpacing = 0.14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(98, 129, 153))
            setPadding(0, dp(18), 0, dp(10))
        }, matchWrap())
        val localButton = Button(this).apply {
            text = "Usar modo local sem conta"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.rgb(199, 225, 244))
            background = rounded(Color.rgb(16, 31, 49), Color.rgb(36, 82, 113), 14)
            minHeight = dp(50)
            setOnClickListener {
                AlertDialog.Builder(this@LoginActivity)
                    .setTitle("Entrar sem conta")
                    .setMessage("O modo local não registra um usuário global, não cria um proprietário e não libera recursos de conta. Você poderá mudar para o login Google depois que OAuth e backend forem configurados.")
                    .setNegativeButton("Voltar", null)
                    .setPositiveButton("Continuar local") { _, _ ->
                        JarvisAuthState.saveLocal(this@LoginActivity)
                        openAssistant()
                    }
                    .show()
            }
        }
        page.addView(localButton, LinearLayout.LayoutParams(-1, dp(50)))
        page.addView(TextView(this).apply {
            text = "Privacidade: comandos e conversas locais ficam neste telefone. Consultas Web são enviadas ao mecanismo de busca. O login Google compartilha perfil/token apenas com o backend configurado para validar a identidade."
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(105, 132, 154))
            setPadding(dp(4), dp(22), dp(4), 0)
        }, matchWrap())
        SystemBarInsets.apply(scroll)
        setContentView(scroll)
    }

    private fun beginGoogleSignIn() {
        googleButton.isEnabled = false
        status.text = "Conectando ao Google…"
        scope.launch {
            try {
                val account = JarvisAuthRepository.signInWithGoogle(this@LoginActivity)
                JarvisAuthState.saveGoogle(this@LoginActivity, account)
                Toast.makeText(
                    this@LoginActivity,
                    if (account.isOwner) "Conta autenticada: proprietário global." else "Conta autenticada.",
                    Toast.LENGTH_LONG
                ).show()
                openAssistant()
            } catch (e: Exception) {
                status.text = e.message ?: "Não consegui autenticar. Verifique a conexão e a configuração do projeto."
                googleButton.isEnabled = true
            }
        }
    }

    private fun openAssistant() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
