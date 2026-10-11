package com.neuralhud.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.pm.PackageManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ToolboxActivity : Activity() {
    private lateinit var query: EditText
    private lateinit var result: TextView
    private lateinit var model: LocalNeuralModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = LocalNeuralModel(this)
        window.statusBarColor = Color.rgb(16, 24, 32)
        window.navigationBarColor = Color.rgb(16, 24, 32)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(20))
            setBackgroundColor(Color.rgb(16, 24, 32))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val title = TextView(this).apply {
            text = "NEURAL HUD • FERRAMENTAS"
            textSize = 22f
            setTextColor(Color.rgb(77, 208, 225))
        }
        content.addView(title)
        val help = TextView(this).apply {
            text = "Pesquisa e inspeção local. Nenhum texto é enviado a uma API de IA."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(12))
        }
        content.addView(help)

        query = EditText(this).apply {
            hint = "Texto para classificar ou pesquisar"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            minLines = 2
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(29, 43, 54))
        }
        content.addView(query, matchWrap())
        addButton(content, "Pesquisar no Google") {
            val q = query.text.toString().trim()
            if (q.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
            } else {
                show("Digite uma consulta primeiro.")
            }
        }
        addButton(content, "Classificar com a rede neural local") {
            show("Classificação local: ${model.classify(query.text.toString())}")
        }
        addButton(content, "Ensinar categoria: aplicativo") { train(0) }
        addButton(content, "Ensinar categoria: pesquisa") { train(1) }
        addButton(content, "Ensinar categoria: arquivo") { train(2) }
        addButton(content, "Ensinar categoria: HUD") { train(3) }
        addButton(content, "Ensinar categoria: aprendizado") { train(4) }
        addButton(content, "Ensinar categoria: problema") { train(5) }
        addButton(content, "Selecionar arquivo para inspecionar") {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(picker, REQUEST_PICK_FILE)
        }
        addButton(content, "Listar apps permitidos pelo Android") { listInstalledApps() }
        addButton(content, "Voltar ao HUD") { finish() }

        result = TextView(this).apply {
            text = "Pronto."
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setBackgroundColor(Color.rgb(29, 43, 54))
        }
        page.addView(result, LinearLayout.LayoutParams(-1, dp(180)).apply {
            topMargin = dp(12)
        })
        setContentView(page)
    }

    private fun train(label: Int) {
        model.feedback(query.text.toString(), label)
        show("Feedback de aprendizado salvo localmente.")
    }

    private fun listInstalledApps() {
        val apps = try {
            if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }
        } catch (e: Exception) {
            show("O Android restringiu a lista de apps: ${e.message ?: "acesso indisponível"}")
            return
        }
        val visible = apps.sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }.take(40)
        show(visible.joinToString("\n") {
            "${packageManager.getApplicationLabel(it)} • ${it.packageName}"
        }.ifBlank { "Nenhum aplicativo foi disponibilizado pelo Android." })
    }

    @Deprecated("Uses the native Android document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_FILE && resultCode == RESULT_OK) {
            data?.data?.let { inspectFile(it) }
        }
    }

    private fun inspectFile(uri: Uri) {
        try {
            val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: uri.lastPathSegment ?: "arquivo"
            val limit = if (name.endsWith(".apk", true) || name.endsWith(".zip", true)) 512 * 1024 else 64 * 1024
            val bytes = contentResolver.openInputStream(uri)?.use { readLimited(it, limit) }
                ?: throw IllegalStateException("O Android não forneceu acesso ao arquivo.")
            if (name.endsWith(".apk", true) || name.endsWith(".zip", true)) {
                val names = mutableListOf<String>()
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    while (names.size < 50) {
                        val entry = zip.nextEntry ?: break
                        names.add(entry.name)
                        zip.closeEntry()
                    }
                }
                show("Arquivo: $name\nAmostra limitada de entradas ZIP/APK:\n" + names.joinToString("\n"))
            } else {
                show("Arquivo: $name\nAmostra lida: ${bytes.size} bytes\nCategoria local: ${model.classify(bytes.toString(Charsets.UTF_8))}\nNenhum conteúdo foi enviado à Web.")
            }
        } catch (e: Exception) {
            show("Falha na inspeção: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun readLimited(input: java.io.InputStream, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (output.size() < maximum) {
            val read = input.read(buffer, 0, minOf(buffer.size, maximum - output.size()))
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun show(text: String) {
        result.text = text
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(39, 69, 83))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(6)
        })
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PICK_FILE = 5402
    }
}
