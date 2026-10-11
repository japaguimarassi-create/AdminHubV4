package com.jarvis.assistant

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ToolboxActivity : Activity() {
    private lateinit var searchInput: EditText
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 22, 33)
        window.navigationBarColor = Color.rgb(13, 22, 33)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(14))
            setBackgroundColor(Color.rgb(13, 22, 33))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "FERRAMENTAS"
            textSize = 23f
            setTextColor(Color.rgb(91, 224, 238))
        })
        header.addView(TextView(this).apply {
            text = "Três ações simples. Sem conta e sem API de IA."
            textSize = 13f
            setTextColor(Color.rgb(174, 194, 207))
            setPadding(0, dp(4), 0, dp(12))
        })
        page.addView(header)

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        addSection(body, "01  •  ARQUIVOS", "Abra um documento pelo seletor do Android. O editor prepara a alteração e cria um backup antes de gravar.")
        addButton(body, "Editar arquivo") {
            startActivity(Intent(this, TextFileEditorActivity::class.java))
        }

        addSection(body, "02  •  PESQUISA WEB", "A busca abre no navegador padrão; a consulta é enviada ao site que você escolheu.")
        searchInput = EditText(this).apply {
            hint = "O que pesquisar?"
            setHintTextColor(Color.rgb(145, 167, 181))
            setTextColor(Color.WHITE)
            textSize = 15f
            setSingleLine(true)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.rgb(23, 36, 48), Color.rgb(44, 78, 95))
        }
        body.addView(searchInput, LinearLayout.LayoutParams(-1, dp(52)))
        addButton(body, "Pesquisar no Google") {
            val query = searchInput.text.toString().trim()
            if (query.isEmpty()) {
                show("Digite uma consulta.")
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
                } catch (_: Exception) {
                    show("Nenhum navegador conseguiu abrir a pesquisa.")
                }
            }
        }

        addSection(body, "03  •  APLICATIVOS", "Mostra os aplicativos que o Android disponibiliza como lançáveis. Não pede acesso irrestrito a todos os pacotes.")
        addButton(body, "Listar aplicativos lançáveis") { listLaunchableApps() }

        output = TextView(this).apply {
            text = "Pronto."
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.TOP
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(20, 32, 43), Color.rgb(37, 65, 80))
        }
        body.addView(output, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        val footer = TextView(this).apply {
            text = "O editor é local e não chama uma IA externa. A automação entre apps exige ativação manual em Acessibilidade."
            textSize = 11f
            setTextColor(Color.rgb(145, 167, 181))
            setPadding(0, dp(12), 0, dp(6))
        }
        body.addView(footer)

        addButton(page, "Voltar") { finish() }
        SystemBarInsets.apply(page)
        setContentView(page)
    }

    private fun listLaunchableApps() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val apps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
                .take(60)
            show(apps.joinToString("\n") { item ->
                item.loadLabel(packageManager).toString() + " • " + item.activityInfo.packageName
            }.ifBlank { "O Android não retornou aplicativos lançáveis." })
        } catch (e: Exception) {
            show("Não foi possível consultar a lista: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun addSection(parent: LinearLayout, title: String, description: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 11f
            letterSpacing = 0.05f
            setTextColor(Color.rgb(91, 224, 238))
            setPadding(dp(1), dp(14), dp(1), dp(5))
        })
        parent.addView(TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(Color.rgb(174, 194, 207))
            setPadding(dp(1), 0, dp(1), dp(7))
        })
    }

    private fun addButton(parent: LinearLayout, title: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            minHeight = dp(48)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(34, 51, 65), Color.rgb(56, 86, 104))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(5) })
    }

    private fun show(message: String) {
        output.text = message
    }

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
