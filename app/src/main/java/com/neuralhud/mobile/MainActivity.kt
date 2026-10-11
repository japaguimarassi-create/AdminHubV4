package com.neuralhud.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var query: EditText
    private lateinit var model: LocalNeuralModel

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inspectFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = LocalNeuralModel(this)
        window.statusBarColor = Color.rgb(16, 24, 32)
        window.navigationBarColor = Color.rgb(16, 24, 32)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(22))
            setBackgroundColor(Color.rgb(16, 24, 32))
        }
        val scroll = ScrollView(this).apply { addView(page) }
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "NEURAL HUD"
            setTextColor(Color.rgb(77, 208, 225))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
            gravity = Gravity.CENTER_VERTICAL
        }
        page.addView(title, matchWrap())
        val subtitle = TextView(this).apply {
            text = "Overlay + rede neural local • sem API de IA"
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(18))
            setTextSize(14f)
        }
        page.addView(subtitle, matchWrap())

        status = TextView(this).apply {
            text = "Pronto. Ative a esfera e escolha os acessos manualmente."
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(29, 43, 54))
        }
        page.addView(status, matchWrap())

        button(page, "Permitir esfera flutuante") {
            if (Settings.canDrawOverlays(this)) {
                startHud()
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                status.text = "Permita 'Exibir sobre outros apps' e volte para ativar o HUD."
            }
        }
        button(page, "Ativar / reabrir HUD") { startHud() }
        button(page, "Minimizar e encerrar HUD") {
            stopService(Intent(this, HudOverlayService::class.java))
            status.text = "HUD encerrado."
        }
        button(page, "Configurar leitura opcional da interface") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            status.text = "Ative 'Neural HUD — leitura de interface' somente se quiser usar a classificação de interface."
        }
        button(page, "Listar apps instalados permitidos pelo Android") { showInstalledApps() }
        button(page, "Selecionar arquivo para inspeção") { filePicker.launch(arrayOf("*/*")) }

        val searchLabel = TextView(this).apply {
            text = "Pesquisa Google (abre o navegador; não usa API)"
            setTextColor(Color.rgb(77, 208, 225))
            setPadding(0, dp(18), 0, dp(6))
            textSize = 16f
        }
        page.addView(searchLabel, matchWrap())
        query = EditText(this).apply {
            hint = "Digite sua pesquisa ou um texto para classificar"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setSingleLine(false)
            minLines = 2
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(29, 43, 54))
        }
        page.addView(query, matchWrap())
        button(page, "Pesquisar no Google") {
            val q = query.text.toString().trim()
            if (q.isNotEmpty()) {
                val url = "https://www.google.com/search?q=" + Uri.encode(q)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } else {
                status.text = "Digite a consulta primeiro."
            }
        }
        button(page, "Classificar com a rede neural local") {
            val text = query.text.toString()
            status.text = "Classificação local: ${model.classify(text)}\nOs dados ficam no dispositivo."
        }

        val feedbackLabel = TextView(this).apply {
            text = "Feedback de aprendizado"
            setTextColor(Color.rgb(77, 208, 225))
            setPadding(0, dp(14), 0, dp(5))
            textSize = 16f
        }
        page.addView(feedbackLabel, matchWrap())
        val choices = listOf(
            "App" to 0, "Pesquisa" to 1, "Arquivo" to 2,
            "HUD" to 3, "Aprendizado" to 4, "Problema" to 5
        )
        choices.forEach { (label, id) ->
            button(page, "Ensinar: $label") {
                model.feedback(query.text.toString(), id)
                status.text = "Feedback salvo localmente: $label."
            }
        }
        button(page, "Parar leitura e esquecer última categoria observada") {
            getSharedPreferences("neural_hud_observer", MODE_PRIVATE).edit().clear().apply()
            status.text = "Categorias locais observadas foram removidas."
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized && !Settings.canDrawOverlays(this)) {
            status.text = "Sobreposição ainda não autorizada. Use 'Permitir esfera flutuante'."
        }
    }

    private fun startHud() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "O Android ainda não autorizou a sobreposição."
            Toast.makeText(this, "Permita a sobreposição nas configurações.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, HudOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        status.text = "Solicitação enviada. A esfera Neural HUD deve aparecer sobre outros apps."
    }

    private fun showInstalledApps() {
        val apps = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            }
        } catch (e: Exception) {
            status.text = "O Android restringiu a lista de aplicativos: ${e.message ?: "acesso indisponível"}"
            return
        }
        val visible = apps.sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }.take(35)
        val text = visible.joinToString("\n") {
            "${packageManager.getApplicationLabel(it)}  •  ${it.packageName}"
        }
        status.text = "Apps que o Android permitiu listar (${visible.size} mostrados):\n$text"
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
                    while (names.size < 40) {
                        val entry = zip.nextEntry ?: break
                        names.add(entry.name)
                        zip.closeEntry()
                    }
                }
                val summary = names.joinToString("\n").ifBlank { "Nenhum item legível no trecho selecionado." }
                status.text = "Arquivo: $name\nItens iniciais do ZIP/APK:\n$summary\n\nAmostra limitada; o arquivo completo não foi enviado a lugar algum."
            } else {
                val text = bytes.toString(Charsets.UTF_8)
                val category = model.classify(text)
                status.text = "Arquivo: $name\nTamanho da amostra: ${bytes.size} bytes\nCategoria local: $category\nO conteúdo bruto não foi enviado à Web."
            }
        } catch (e: Exception) {
            status.text = "Não foi possível inspecionar o arquivo: ${e.message ?: e.javaClass.simpleName}"
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

    private fun button(parent: LinearLayout, text: String, action: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(39, 69, 83))
            isAllCaps = false
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(6)
        parent.addView(button, params)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
