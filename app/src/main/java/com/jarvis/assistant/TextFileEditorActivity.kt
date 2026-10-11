package com.jarvis.assistant

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

class TextFileEditorActivity : Activity() {
    private lateinit var instruction: EditText
    private lateinit var editor: EditText
    private lateinit var fileLabel: TextView
    private lateinit var preview: TextView
    private lateinit var previewButton: Button
    private lateinit var status: TextView
    private var previewExpanded = false
    private var currentUri: Uri? = null
    private var currentName = ""
    private var originalBytes = byteArrayOf()
    private var originalText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 24, 32)
        window.navigationBarColor = Color.rgb(16, 24, 32)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
            setBackgroundColor(Color.rgb(13, 22, 33))
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        body.addView(TextView(this).apply {
            text = "EDITOR DE ARQUIVOS"
            textSize = 22f
            setTextColor(Color.rgb(91, 224, 238))
        })
        body.addView(TextView(this).apply {
            text = "Selecione um arquivo de texto. Revise a proposta e confirme para salvar com backup."
            textSize = 13f
            setTextColor(Color.rgb(174, 194, 207))
            setPadding(0, dp(4), 0, dp(12))
        })

        fileLabel = label("Nenhum arquivo selecionado.")
        fileLabel.setPadding(dp(10), dp(10), dp(10), dp(10))
        fileLabel.background = rounded(Color.rgb(20, 32, 43))
        body.addView(fileLabel)
        addButton(body, "1. Selecionar arquivo") { chooseFile() }

        body.addView(label("2. Instrução de edição"))
        instruction = EditText(this).apply {
            hint = "Ex.: substituir \"modo antigo\" por \"modo novo\""
            setHintTextColor(Color.rgb(145, 167, 181))
            setTextColor(Color.WHITE)
            textSize = 14f
            minLines = 2
            maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(11), dp(10), dp(11), dp(10))
            background = rounded(Color.rgb(23, 36, 48))
        }
        body.addView(instruction, matchWrap())
        addButton(body, "Preparar alteração") { prepareChange() }

        body.addView(label("3. Conteúdo para revisar"))
        editor = EditText(this).apply {
            hint = "Selecione um arquivo primeiro."
            setHintTextColor(Color.rgb(145, 167, 181))
            setTextColor(Color.WHITE)
            textSize = 13f
            minLines = 8
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(11), dp(10), dp(11), dp(10))
            background = rounded(Color.rgb(20, 32, 43))
            isEnabled = false
        }
        body.addView(editor, LinearLayout.LayoutParams(-1, dp(220)).apply { topMargin = dp(5) })

        preview = TextView(this).apply {
            text = "A prévia aparecerá aqui."
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(Color.rgb(20, 32, 43))
            visibility = View.GONE
        }
        previewButton = addButton(body, "Mostrar prévia") {
            previewExpanded = !previewExpanded
            preview.visibility = if (previewExpanded) View.VISIBLE else View.GONE
            previewButton.text = if (previewExpanded) "Ocultar prévia" else "Mostrar prévia"
            if (previewExpanded) updatePreview()
        }
        body.addView(preview, matchWrap())
        addButton(body, "Salvar com backup") { confirmSave() }
        addButton(body, "Restaurar último backup") { confirmRestore() }

        status = TextView(this).apply {
            text = "Pronto. Nenhum arquivo foi alterado."
            textSize = 12f
            setTextColor(Color.rgb(128, 222, 234))
            setPadding(dp(8), dp(10), dp(8), dp(10))
            background = rounded(Color.rgb(20, 32, 43))
        }
        page.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        addButton(page, "Voltar às ferramentas") { finish() }
        SystemBarInsets.apply(page)
        setContentView(page)
    }

    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PICK_TEXT)
    }

    @Deprecated("Uses the native Android document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_TEXT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val persistentFlags = (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) or (data.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (persistentFlags != 0) {
                try { contentResolver.takePersistableUriPermission(uri, persistentFlags) } catch (_: Exception) {}
            }
            val name = queryDisplayName(uri)
            val bytes = readBounded(uri)
            val text = decodeText(bytes)
            currentUri = uri
            currentName = name
            originalBytes = bytes
            originalText = text
            fileLabel.text = "Arquivo: $name\nOrigem concedida pelo seletor Android"
            editor.setText(text)
            editor.isEnabled = true
            preview.text = renderPreview(originalText, originalText)
            setStatus("Arquivo carregado. Ele ainda não foi modificado.")
        } catch (e: Exception) {
            currentUri = null
            currentName = ""
            originalBytes = byteArrayOf()
            originalText = ""
            editor.setText("")
            editor.isEnabled = false
            fileLabel.text = "Arquivo não carregado."
            setStatus("Não foi possível editar este arquivo: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: "arquivo"
    }

    private fun readBounded(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("o provedor não permitiu ler o arquivo")
        return input.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES) throw IllegalArgumentException("arquivo maior que 512 KiB")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.any { it == 0.toByte() }) throw IllegalArgumentException("o conteúdo parece binário; arquivos binários não são alterados")
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun prepareChange() {
        if (currentUri == null) {
            setStatus("Selecione um arquivo antes de preparar a alteração.")
            return
        }
        val command = instruction.text.toString().trim()
        if (command.isBlank()) {
            setStatus("Digite uma instrução de edição.")
            return
        }
        val source = editor.text.toString()
        try {
            val changed = applyLocalInstruction(source, command)
            if (changed == source) {
                setStatus("A instrução não alterou o conteúdo. Verifique se o texto-alvo existe e se o comando é compatível.")
                return
            }
            editor.setText(changed)
            updatePreview()
            setStatus("Proposta preparada localmente. Confira a prévia e o conteúdo antes de salvar.")
        } catch (e: IllegalArgumentException) {
            setStatus(e.message ?: "Instrução não reconhecida.")
        }
    }

    private fun applyLocalInstruction(source: String, command: String): String {
        val replaceAll = Regex("(?is)^(?:substitua|substituir)\\s+tudo\\s+por\\s*:\\s*(.*)$").matchEntire(command)
        if (replaceAll != null) return replaceAll.groupValues[1]
        val replace = Regex("(?is)^(?:substitua|substituir|troque|trocar)\\s+(.+?)\\s+(?:por|para)\\s+(.+)$").matchEntire(command)
        if (replace != null) {
            val old = unquote(replace.groupValues[1])
            val fresh = unquote(replace.groupValues[2])
            require(old.isNotEmpty()) { "Informe o texto que será substituído." }
            require(source.contains(old)) { "O texto alvo não foi encontrado no arquivo." }
            return source.replace(old, fresh)
        }
        val append = Regex("(?is)^(?:adicionar|adicione|acrescentar|acrescente)\\s+(?:ao final|no final|ao fim)\\s*:\\s*(.*)$").matchEntire(command)
        if (append != null) {
            val addition = append.groupValues[1]
            return source + (if (source.isNotEmpty() && !source.endsWith("\n")) "\n" else "") + addition
        }
        val prepend = Regex("(?is)^(?:adicionar|adicione|inserir|insira)\\s+(?:no início|no inicio|ao início|ao inicio)\\s*:\\s*(.*)$").matchEntire(command)
        if (prepend != null) {
            val addition = prepend.groupValues[1]
            return addition + (if (addition.isNotEmpty() && !addition.endsWith("\n") && source.isNotEmpty()) "\n" else "") + source
        }
        val removeLines = Regex("(?is)^(?:remova|remover|apague|apagar)\\s+linhas?\\s+contendo\\s+(.+)$").matchEntire(command)
        if (removeLines != null) {
            val needle = unquote(removeLines.groupValues[1])
            require(needle.isNotEmpty()) { "Informe o texto usado para localizar as linhas." }
            require(source.contains(needle)) { "O texto alvo não foi encontrado no arquivo." }
            return source.split("\n").filterNot { it.contains(needle) }.joinToString("\n")
        }
        val removeText = Regex("(?is)^(?:remova|remover|apague|apagar)\\s+texto\\s*:\\s*(.*)$").matchEntire(command)
        if (removeText != null) {
            val needle = unquote(removeText.groupValues[1])
            require(needle.isNotEmpty()) { "Informe o texto que será removido." }
            require(source.contains(needle)) { "O texto alvo não foi encontrado no arquivo." }
            return source.replace(needle, "")
        }
        throw IllegalArgumentException("Instrução não reconhecida. Use: “substitua X por Y”, “adicionar ao final: texto”, “adicionar no início: texto”, “remover linhas contendo X”, “remover texto: X” ou “substitua tudo por: texto”.")
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'') ||
                (first == '“' && last == '”') || (first == '‘' && last == '’')) {
                return trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }

    private fun updatePreview() {
        val uri = currentUri ?: run {
            setStatus("Selecione um arquivo primeiro.")
            return
        }
        val proposed = editor.text.toString()
        if (proposed.toByteArray(StandardCharsets.UTF_8).size > MAX_BYTES) {
            setStatus("A proposta ultrapassa 512 KiB. Reduza o conteúdo antes de salvar.")
            return
        }
        preview.text = renderPreview(originalText, proposed)
        setStatus(if (proposed == originalText) "A proposta é igual ao original; nenhuma gravação necessária." else "Prévia atualizada. O arquivo original permanece intacto até você confirmar a gravação.")
    }

    private fun renderPreview(before: String, after: String): String {
        fun excerpt(text: String): String {
            val limit = 2600
            return if (text.length <= limit) text else text.take(limit) + "\n… [conteúdo truncado na prévia]"
        }
        return "ORIGINAL • ${before.length} caracteres\n${excerpt(before)}\n\nPROPOSTA • ${after.length} caracteres\n${excerpt(after)}"
    }

    private fun confirmSave() {
        val uri = currentUri
        if (uri == null) {
            setStatus("Selecione um arquivo primeiro.")
            return
        }
        val proposed = editor.text.toString()
        val bytes = proposed.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_BYTES) {
            setStatus("A proposta ultrapassa o limite de 512 KiB.")
            return
        }
        if (proposed == originalText) {
            setStatus("Nenhuma alteração pendente.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Confirmar edição")
            .setMessage("O arquivo “$currentName” será substituído pelo conteúdo da proposta. Antes, o conteúdo atual será copiado para o backup privado do J.A.R.V.I.S.. Só continue se conferiu a prévia.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Criar backup e salvar") { _, _ -> saveChanges(uri, bytes, proposed) }
            .show()
    }

    private fun saveChanges(uri: Uri, bytes: ByteArray, proposed: String) {
        var backup: File? = null
        try {
            val dir = File(filesDir, "file-backups")
            if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("não foi possível criar a pasta de backup")
            backup = File(dir, "${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.bak")
            backup.writeBytes(originalBytes)
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_BACKUP_PATH, backup.absolutePath)
                .putString(KEY_BACKUP_URI, uri.toString())
                .putString(KEY_BACKUP_NAME, currentName)
                .apply()

            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(bytes)
                it.flush()
            } ?: throw IllegalStateException("o provedor não permitiu gravar o arquivo")
            val verify = readBounded(uri)
            if (!verify.contentEquals(bytes)) throw IllegalStateException("a verificação pós-gravação não corresponde à proposta; o backup foi preservado")

            originalBytes = bytes
            originalText = proposed
            editor.setText(proposed)
            preview.text = renderPreview(originalText, proposed)
            setStatus("Arquivo salvo e verificado. Backup original criado no armazenamento privado do app. Use “Restaurar último backup” para desfazer.")
        } catch (e: Exception) {
            setStatus("Falha ao gravar ou verificar: ${e.message ?: e.javaClass.simpleName}. ${if (backup?.exists() == true) "O backup local foi preservado." else "Nenhum backup confirmado."}")
        }
    }

    private fun confirmRestore() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val backupPath = prefs.getString(KEY_BACKUP_PATH, null)
        val uriText = prefs.getString(KEY_BACKUP_URI, null)
        if (backupPath.isNullOrBlank() || uriText.isNullOrBlank() || !File(backupPath).isFile) {
            setStatus("Ainda não há um backup para restaurar.")
            return
        }
        val name = prefs.getString(KEY_BACKUP_NAME, "arquivo") ?: "arquivo"
        AlertDialog.Builder(this)
            .setTitle("Restaurar backup")
            .setMessage("O conteúdo salvo antes da última edição de “$name” será escrito de volta no documento original.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Restaurar") { _, _ -> restoreBackup(File(backupPath), Uri.parse(uriText)) }
            .show()
    }

    private fun restoreBackup(backup: File, uri: Uri) {
        try {
            val bytes = backup.readBytes()
            if (bytes.size > MAX_BYTES) throw IllegalStateException("backup maior que o limite suportado")
            val text = decodeText(bytes)
            contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(bytes)
                it.flush()
            } ?: throw IllegalStateException("o Android não permitiu gravar no documento original")
            if (!readBounded(uri).contentEquals(bytes)) throw IllegalStateException("a verificação da restauração falhou")
            if (currentUri == uri) {
                originalBytes = bytes
                originalText = text
                editor.setText(text)
                preview.text = renderPreview(text, text)
            }
            setStatus("Backup restaurado e verificado.")
        } catch (e: Exception) {
            setStatus("Não foi possível restaurar: ${e.message ?: e.javaClass.simpleName}. Talvez seja necessário selecionar novamente o documento e conceder acesso.")
        }
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        letterSpacing = 0.04f
        setTextColor(Color.rgb(153, 198, 212))
        setPadding(dp(1), dp(12), dp(1), dp(5))
    }

    private fun setStatus(value: String) {
        status.text = value
    }

    private fun addButton(parent: LinearLayout, title: String, action: () -> Unit): Button {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 13f
            minHeight = dp(46)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(34, 51, 65))
            setOnClickListener { action() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(5)
        })
        return button
    }

    private fun rounded(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(13).toFloat()
        setStroke(dp(1), Color.rgb(50, 75, 92))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PICK_TEXT = 7601
        private const val MAX_BYTES = 512 * 1024
        private const val PREFS = "neural_hud_file_editor"
        private const val KEY_BACKUP_PATH = "last_backup_path"
        private const val KEY_BACKUP_URI = "last_backup_uri"
        private const val KEY_BACKUP_NAME = "last_backup_name"
    }
}
