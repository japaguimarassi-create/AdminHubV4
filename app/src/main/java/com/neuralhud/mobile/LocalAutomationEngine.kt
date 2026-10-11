package com.neuralhud.mobile

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import java.text.Normalizer
import java.util.Locale

enum class LocalActionType {
    SEARCH, TYPE_TEXT, CLICK_TEXT, BACK, HOME, ENTER, SCROLL_DOWN, SCROLL_UP, OPEN_APP, WAIT
}

data class LocalAction(val type: LocalActionType, val argument: String = "")

data class ParsedInstruction(
    val original: String,
    val targetApp: String?,
    val actions: List<LocalAction>,
    val actionText: String
)

object LocalAutomationEngine {
    private const val PREFS = "neural_hud_automation"
    private const val KEY_RAW = "raw_instruction"
    private const val KEY_TARGET = "target_app"
    private const val KEY_ACTIVE = "active"
    private const val KEY_ARMED_IGNORE = "ignore_package"
    private const val KEY_LAST_PACKAGE = "last_run_package"
    private const val KEY_LAST_TIME = "last_run_elapsed"
    private const val KEY_STATUS = "last_status"

    fun saveInstruction(context: Context, raw: String): String {
        val parsed = parse(raw) ?: run {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ACTIVE, false)
                .putString(KEY_STATUS, "Instrução não reconhecida. Automação pausada.")
                .apply()
            return "Não entendi a instrução. Exemplos: “Quando abrir YouTube, pesquise gatos” ou “digite Olá”."
        }
        val observerPrefs = context.getSharedPreferences("neural_hud_observer", Context.MODE_PRIVATE)
        val currentPackage = observerPrefs.getString("last_package", "") ?: ""
        val targetDescription = parsed.targetApp ?: "próximo aplicativo aberto"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_RAW, parsed.original)
            .putString(KEY_TARGET, parsed.targetApp ?: "*")
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_ARMED_IGNORE, currentPackage)
            .putString(KEY_LAST_PACKAGE, "")
            .putLong(KEY_LAST_TIME, 0L)
            .putString(KEY_STATUS, "Instrução salva. Aguardando: $targetDescription")
            .apply()
        return "Instrução salva e ativada. Alvo: $targetDescription. Motor local, sem API."
    }

    fun parse(rawText: String): ParsedInstruction? {
        val raw = rawText.trim()
        if (raw.isBlank()) return null

        var target: String? = null
        var actionText = raw
        val patterns = listOf(
            Regex("(?i)^(?:quando|ao|assim que)\\s+(?:eu\\s+)?(?:abrir|abro|entrar\\s+(?:em|no|na))\\s+(.+?)\\s*[,;:]\\s*(.+)$"),
            Regex("(?i)^quando\\s+(?:o\\s+)?app\\s+(.+?)\\s+(?:abrir|for\\s+aberto)\\s*[,;:]\\s*(.+)$")
        )
        for (pattern in patterns) {
            val match = pattern.matchEntire(raw)
            if (match != null) {
                target = match.groupValues[1].trim()
                    .replace(Regex("(?i)^(?:o|a|os|as|app|aplicativo)\\s+"), "")
                actionText = match.groupValues[2].trim()
                break
            }
        }
        val pieces = actionText.split(
            Regex("(?i)\\s*(?:;|\\be\\s+depois\\b|\\bdepois\\b|\\be\\s+então\\b|\\bentão\\b|\\s+e\\s+(?=(?:digite|escreva|insira|toque|clique|pressione|pesquise|procure|busque|volte|role|abra|inicie)))\\s*")
        ).map { it.trim().removePrefix(",").trim() }.filter { it.isNotBlank() }
        if (pieces.isEmpty()) return null
        val actions = pieces.map { parseAction(it) ?: return null }
        return ParsedInstruction(raw, target, actions, actionText)
    }

    private fun parseAction(raw: String): LocalAction? {
        val text = raw.trim().removePrefix(",").trim()
        val normalized = normalize(text)
        if (Regex("^(volte|voltar|retorne|pressione voltar|aperte voltar)$").matches(normalized)) {
            return LocalAction(LocalActionType.BACK)
        }
        if (Regex("^(pressione|aperte|tecla)?\\s*(enter|enviar|pesquisar agora)$").matches(normalized)) {
            return LocalAction(LocalActionType.ENTER)
        }
        if (Regex("^(tela inicial|ir para inicio|ir para home|home|voltar para inicio)$").matches(normalized)) {
            return LocalAction(LocalActionType.HOME)
        }
        if (Regex("^(role para baixo|role abaixo|desca a pagina|role a pagina para baixo|scroll down)$").matches(normalized)) {
            return LocalAction(LocalActionType.SCROLL_DOWN)
        }
        if (Regex("^(role para cima|suba a pagina|role a pagina para cima|scroll up)$").matches(normalized)) {
            return LocalAction(LocalActionType.SCROLL_UP)
        }
        if (normalized == "aguarde" || normalized == "espere") return LocalAction(LocalActionType.WAIT, "600")
        Regex("^(?:aguarde|espere)\\s+(\\d{1,3})\\s*(?:ms|milissegundos)?$").matchEntire(normalized)?.let {
            return LocalAction(LocalActionType.WAIT, it.groupValues[1].toInt().coerceIn(100, 3000).toString())
        }
        Regex("^(?:pesquise|pesquisar|procure|procurar|busque|buscar|pesquisa|busca)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return LocalAction(LocalActionType.SEARCH, cleanArgument(it.groupValues[1])) }
        Regex("^(?:digite|escreva|insira|preencha)\\s+(?:o\\s+texto\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return LocalAction(LocalActionType.TYPE_TEXT, cleanArgument(it.groupValues[1])) }
        Regex("^(?:toque|clique|pressione|selecione)\\s+(?:em|no|na|nos|nas|o|a)?\\s*(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                val label = cleanArgument(it.groupValues[1])
                if (normalize(label) in setOf("voltar", "back")) return LocalAction(LocalActionType.BACK)
                return LocalAction(LocalActionType.CLICK_TEXT, label)
            }
        Regex("^(?:abra|abrir|inicie|iniciar|execute)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.let { return LocalAction(LocalActionType.OPEN_APP, cleanArgument(it.groupValues[1])) }
        return null
    }

    private fun cleanArgument(value: String): String =
        value.trim().trim('"', ''', '“', '”', '‘', '’').trim()

    fun onAppOpened(context: Context, packageName: String): ParsedInstruction? {
        if (packageName.isBlank() || packageName == context.packageName ||
            packageName == "android" || packageName.startsWith("com.android.systemui") ||
            packageName.startsWith("com.android.settings")) return null

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return null

        val ignored = prefs.getString(KEY_ARMED_IGNORE, "") ?: ""
        if (ignored.isNotBlank()) {
            if (packageName == ignored) return null
            prefs.edit().putString(KEY_ARMED_IGNORE, "").apply()
        }

        val target = prefs.getString(KEY_TARGET, "*") ?: "*"
        if (target != "*" && !matchesApp(context, packageName, target)) return null

        val now = SystemClock.elapsedRealtime()
        val lastPackage = prefs.getString(KEY_LAST_PACKAGE, "") ?: ""
        val lastTime = prefs.getLong(KEY_LAST_TIME, 0L)
        if (packageName == lastPackage && now - lastTime < 7000L) return null

        val parsed = parse(prefs.getString(KEY_RAW, "") ?: "") ?: return null
        prefs.edit()
            .putString(KEY_LAST_PACKAGE, packageName)
            .putLong(KEY_LAST_TIME, now)
            .putString(KEY_STATUS, "Executando no app $packageName…")
            .apply()
        if (target == "*") prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
        return parsed
    }

    fun setStatus(context: Context, status: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATUS, status)
            .apply()
        context.sendBroadcast(android.content.Intent(ACTION_STATUS).setPackage(context.packageName).putExtra("status", status))
    }

    const val ACTION_STATUS = "com.neuralhud.mobile.AUTOMATION_STATUS"

    fun getStatus(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATUS, "Nenhuma instrução salva.") ?: "Nenhuma instrução salva."

    fun getInstruction(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RAW, "") ?: ""

    fun isActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, false)
            .putString(KEY_STATUS, "Automação pausada.")
            .apply()
    }

    private fun matchesApp(context: Context, packageName: String, target: String): Boolean {
        val needle = normalize(target)
        if (needle.isBlank()) return false
        if (normalize(packageName).contains(needle)) return true
        return try {
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            normalize(context.packageManager.getApplicationLabel(info).toString()).contains(needle)
        } catch (_: Exception) {
            false
        }
    }

    fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
