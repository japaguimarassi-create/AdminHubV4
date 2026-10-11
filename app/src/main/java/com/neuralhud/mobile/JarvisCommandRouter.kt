package com.neuralhud.mobile

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

object JarvisCommandRouter {
    private val openAppPattern = Regex("(?i)^(?:(?:eu quero|quero|por favor)\\s+)?(?:abra|abrir|abre|inicie|iniciar|inicialize|inicializar|execute|executar|rode|rodar|lance|lancar|launch|open|start)\\s+(?:(?:o|a|app|aplicativo|programa)\\s+)?(.+)$")
    private val searchPattern = Regex("(?i)^(?:pesquise na internet|pesquisar na internet|pesquise|pesquisar|procure|procurar|busque|buscar|pesquisa|busca|encontre|search|google)\\s+(.+)$")
    private val targetSearchPattern = Regex("(?i)^(?:pesquise|pesquisar|procure|procurar|busque|buscar|pesquisa|busca|search)\\s+(.+?)\\s+(?:no|na|em|dentro do|dentro da)\\s+([a-z0-9][a-z0-9 ._+-]+)$")

    fun execute(context: Context, rawText: String): String = executeInternal(context, rawText, 0)

    private fun executeInternal(context: Context, rawText: String, depth: Int): String {
        val appContext = context.applicationContext
        val raw = rawText.trim()
        if (raw.isEmpty()) return "Digite ou fale um comando."
        val normalized = normalize(raw)

        if (depth == 0) {
            JarvisMemory.teach(appContext, raw)?.let { return it }
        }
        if (depth < 4) {
            val learned = JarvisMemory.resolve(appContext, raw)
            if (learned != null && normalize(learned) != normalized) {
                return executeInternal(appContext, learned, depth + 1)
            }
        }

        if (Regex("^(?:ajuda|help|comandos|o que voce sabe fazer|quais sao suas funcoes)$").matches(normalized)) {
            return helpText()
        }
        if (Regex("^(?:parar|pause|pausar|cancele|cancelar|desativar automacao)$").matches(normalized)) {
            LocalAutomationEngine.clear(appContext)
            return "J.A.R.V.I.S.: automação pausada. O HUD continua disponível."
        }
        if (normalized.startsWith("quando abrir ") || normalized.startsWith("ao abrir ") ||
            normalized.startsWith("assim que abrir ") || normalized.startsWith("quando o app ")) {
            return LocalAutomationEngine.saveInstruction(appContext, raw, false)
        }

        val targetSearch = targetSearchPattern.matchEntire(raw)
        if (targetSearch != null) {
            val query = targetSearch.groupValues[1].trim()
            val appName = targetSearch.groupValues[2].trim()
            val launchTarget = findLaunchableApp(appContext, appName)
            if (launchTarget != null && query.isNotBlank()) {
                val instruction = "Quando abrir ${launchTarget.label}, pesquise ${query}"
                val saved = LocalAutomationEngine.saveInstruction(appContext, instruction, false)
                if (launchTarget.packageName != appContext.packageName && launchApp(appContext, launchTarget.label)) {
                    return "J.A.R.V.I.S.: abri ${launchTarget.label}. A pesquisa foi preparada localmente. $saved"
                }
                return "Não consegui iniciar ${launchTarget.label}. $saved"
            }
        }

        val multiApp = Regex("(?i)^(?:abra|abrir|abre|inicie|iniciar|open|launch)\\s+(?:(?:o|a|app|aplicativo)\\s+)?(.+?)\\s+(?:e depois|e|para|pra)\\s+(?:pesquise|pesquisar|procure|procurar|busque|buscar|search)\\s+(.+)$").matchEntire(raw)
        if (multiApp != null) {
            val appName = multiApp.groupValues[1].trim()
            val query = multiApp.groupValues[2].trim()
            val target = findLaunchableApp(appContext, appName)
            if (target != null) {
                val saved = LocalAutomationEngine.saveInstruction(appContext, "Quando abrir ${target.label}, pesquise $query", false)
                val launched = launchApp(appContext, target.label)
                return if (launched) "J.A.R.V.I.S.: abrindo ${target.label}; pesquisa preparada. $saved"
                else "A instrução ficou salva, mas não consegui abrir ${target.label}. $saved"
            }
        }

        if (isTimeRequest(normalized)) {
            val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR")))
            return "Agora são $now."
        }
        if (isDateRequest(normalized)) {
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR")))
            return date.replaceFirstChar { it.uppercase(Locale.forLanguageTag("pt-BR")) } + "."
        }

        val mathExpression = extractMathExpression(raw, normalized)
        if (mathExpression != null) {
            return try {
                val result = MathParser(mathExpression).evaluate()
                "Resultado: ${formatNumber(result)}"
            } catch (_: Exception) {
                "Não consegui calcular essa expressão. Exemplos: “calcule 32 * 45” ou “quanto é (12 + 4) / 2”."
            }
        }

        if (normalized in setOf("voltar", "volte", "retornar", "volte uma tela", "pressione voltar", "back")) {
            return globalAction(appContext, "back")
        }
        if (normalized in setOf("inicio", "tela inicial", "ir para tela inicial", "home", "voltar para home")) {
            return globalAction(appContext, "home")
        }
        if (normalized.contains("notificac") || normalized.contains("central de notificacoes")) {
            return globalAction(appContext, "notifications")
        }
        if (normalized.contains("configuracoes rapidas") || normalized.contains("painel rapido") || normalized == "quick settings") {
            return globalAction(appContext, "quick_settings")
        }
        if (normalized.contains("aplicativos recentes") || normalized == "recentes" || normalized == "recents") {
            return globalAction(appContext, "recents")
        }
        if (normalized.contains("capturar tela") || normalized.contains("fazer screenshot") || normalized == "tirar print") {
            return globalAction(appContext, "screenshot")
        }

        if (normalized.contains("aumentar volume") || normalized == "volume para cima") {
            return adjustVolume(appContext, AudioManager.ADJUST_RAISE)
        }
        if (normalized.contains("diminuir volume") || normalized == "volume para baixo") {
            return adjustVolume(appContext, AudioManager.ADJUST_LOWER)
        }
        if (normalized.contains("silenciar") || normalized == "mutar") {
            return adjustVolume(appContext, AudioManager.ADJUST_MUTE)
        }
        if (normalized in setOf("tocar musica", "pausar musica", "play", "pause", "play pause", "reproduzir ou pausar")) {
            return dispatchMediaKey(appContext)
        }

        val note = Regex("(?i)^(?:anote|anotar|salve uma nota|salvar nota|lembre que|lembre-me de|remember)\\s+(.+)$").matchEntire(raw)
        if (note != null) {
            val value = note.groupValues[1].trim()
            if (value.isNotBlank()) {
                appContext.getSharedPreferences("jarvis_notes", Context.MODE_PRIVATE).edit()
                    .putString("last_note", value)
                    .putLong("last_note_time", System.currentTimeMillis())
                    .apply()
                return "Nota salva localmente: $value"
            }
        }
        if (normalized in setOf("ler minha nota", "mostrar nota", "qual foi minha nota", "read my note")) {
            return appContext.getSharedPreferences("jarvis_notes", Context.MODE_PRIVATE)
                .getString("last_note", null)?.let { "Sua última nota é: $it" } ?: "Ainda não há notas salvas."
        }

        if (normalized.contains("abrir configuracoes") || normalized == "configuracoes" ||
            normalized == "configuracoes do android" || normalized == "abrir ajustes") {
            return launchIntent(appContext, Intent(Settings.ACTION_SETTINGS), "Configurações do Android abertas.")
        }
        if (normalized.contains("wifi") && (normalized.contains("configur") || normalized.startsWith("abrir"))) {
            return launchIntent(appContext, Intent(Settings.ACTION_WIFI_SETTINGS), "Configurações de Wi-Fi abertas.")
        }
        if (normalized.contains("bluetooth") && (normalized.contains("configur") || normalized.startsWith("abrir"))) {
            return launchIntent(appContext, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Configurações de Bluetooth abertas.")
        }
        if (normalized.contains("bateria") && (normalized.contains("configur") || normalized.startsWith("abrir"))) {
            return launchIntent(appContext, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), "Configurações de bateria abertas.")
        }

        val urlCandidate = extractUrl(raw, normalized)
        if (urlCandidate != null) {
            return launchIntent(
                appContext,
                Intent(Intent.ACTION_VIEW, Uri.parse(urlCandidate)).addCategory(Intent.CATEGORY_BROWSABLE),
                "Abrindo o endereço solicitado."
            )
        }

        val openApp = openAppPattern.matchEntire(raw)
        if (openApp != null) {
            val requested = cleanAppName(openApp.groupValues[1])
            val launchTarget = findLaunchableApp(appContext, requested)
            if (launchTarget != null) {
                return if (launchApp(appContext, launchTarget.label)) "Abrindo ${launchTarget.label}."
                else "O Android não permitiu abrir ${launchTarget.label}."
            }
            if (requested.contains(".") || requested.contains("www")) {
                return launchIntent(appContext, Intent(Intent.ACTION_VIEW, Uri.parse("https://" + requested.removePrefix("https://"))), "Abrindo o site.")
            }
            return "Não encontrei um aplicativo lançável com o nome “$requested”. Tente o nome mostrado na tela inicial."
        }

        val search = searchPattern.matchEntire(raw)
        if (search != null) return launchWebSearch(appContext, search.groupValues[1].trim())
        val parsed = LocalAutomationEngine.parse(raw)
        if (parsed != null && parsed.targetApp == null &&
            parsed.actions.none { it.type == LocalActionType.OPEN_APP }) {
            if (!isAccessibilityEnabled(appContext)) {
                return "Entendi uma ação para outro aplicativo, mas a automação global está desligada. Ative J.A.R.V.I.S. em Acessibilidade para executá-la."
            }
            appContext.sendBroadcast(
                Intent(AppObserverService.ACTION_EXECUTE_NOW)
                    .setPackage(appContext.packageName)
                    .putExtra(AppObserverService.EXTRA_INSTRUCTION, raw)
            )
            return "Instrução enviada ao núcleo de automação local."
        }

        if (normalized.contains("?") || startsWithQuestion(normalized)) {
            return launchWebSearch(appContext, raw)
        }

        val guess = JarvisLocalBrain.classify(appContext, raw)
        LocalAutomationEngine.setStatus(
            appContext,
            "Pedido não mapeado como ação local. Categoria estimada pelo classificador: " + guess.intent.name.lowercase(Locale.ROOT).replace('_', ' ') + ". Abrindo pesquisa Web."
        )
        return launchWebSearch(appContext, raw)
    }

    private fun globalAction(context: Context, action: String): String {
        if (!isAccessibilityEnabled(context)) {
            return "Para executar “$action” em qualquer tela, ative o serviço J.A.R.V.I.S. em Configurações > Acessibilidade."
        }
        context.sendBroadcast(
            Intent(AppObserverService.ACTION_GLOBAL_COMMAND)
                .setPackage(context.packageName)
                .putExtra(AppObserverService.EXTRA_GLOBAL_ACTION, action)
        )
        return "Comando global enviado: $action."
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo?.serviceInfo?.packageName == context.packageName &&
                it.resolveInfo?.serviceInfo?.name == AppObserverService::class.java.name
        }
    }

    private data class LaunchTarget(val label: String, val packageName: String)

    private fun findLaunchableApp(context: Context, requested: String): LaunchTarget? {
        val target = normalize(cleanAppName(requested))
        if (target.isBlank()) return null
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .asSequence()
                .filter { it.activityInfo.packageName != context.packageName }
                .map { LaunchTarget(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
                .distinctBy { it.packageName }
                .sortedBy { item ->
                    when {
                        normalize(item.label) == target -> 0
                        normalize(item.label).startsWith(target) -> 1
                        normalize(item.label).contains(target) -> 2
                        item.packageName.contains(target) -> 3
                        else -> 9
                    }
                }
                .firstOrNull { item ->
                    normalize(item.label) == target ||
                        normalize(item.label).startsWith(target) ||
                        normalize(item.label).contains(target) ||
                        item.packageName.contains(target)
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun launchApp(context: Context, label: String): Boolean {
        val app = findLaunchableApp(context, label) ?: return false
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun launchIntent(context: Context, intent: Intent, success: String): String {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) "Nenhum aplicativo pode executar essa ação."
            else {
                context.startActivity(intent)
                success
            }
        } catch (e: Exception) {
            "O Android bloqueou essa ação: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun launchWebSearch(context: Context, query: String): String {
        if (query.isBlank()) return "Informe o que deseja pesquisar."
        return launchIntent(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))),
            "Pesquisa aberta no navegador. O núcleo de IA continua sem API; a pesquisa requer Internet."
        )
    }

    private fun adjustVolume(context: Context, direction: Int): String {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            when (direction) {
                AudioManager.ADJUST_RAISE -> "Volume de mídia aumentado."
                AudioManager.ADJUST_LOWER -> "Volume de mídia diminuído."
                else -> "Volume de mídia silenciado."
            }
        } catch (_: Exception) {
            "Não consegui alterar o volume."
        }
    }

    private fun dispatchMediaKey(context: Context): String {
        return try {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            "Comando reproduzir/pausar enviado ao aplicativo de mídia ativo."
        } catch (_: Exception) {
            "Não consegui controlar a mídia ativa."
        }
    }

    private fun extractUrl(raw: String, normalized: String): String? {
        val match = Regex("(?i)^(?:abra|abrir|acesse|acessar|visite|entre no site|go to)\\s+(https?://\\S+|www\\.\\S+|[a-z0-9-]+(?:\\.[a-z0-9-]+)+)(?:\\s*)$").matchEntire(raw.trim())
            ?: return null
        val value = match.groupValues[1].trim().trimEnd('.', ',', ';')
        return if (value.startsWith("http://", true) || value.startsWith("https://", true)) value else "https://$value"
    }

    private fun extractMathExpression(raw: String, normalized: String): String? {
        val match = Regex("(?i)^(?:calcule|calcular|calcula|quanto e|quanto da|quanto eh|resolva|resolver|compute|calculate)\\s+(.+)$").matchEntire(raw.trim())
        if (match != null) {
            val expression = normalizeMath(match.groupValues[1])
            if (expression.any { it.isDigit() } && expression.any { it in "+-*/^()" }) return expression
        }
        if (raw.length <= 100 && raw.any { it.isDigit() } && raw.any { it in "+*/^()" } &&
            raw.all { it.isDigit() || it.isWhitespace() || it in "+-*/^().," }) return normalizeMath(raw)
        if (normalized.startsWith("quanto e ") || normalized.startsWith("quanto da ")) {
            val expression = normalizeMath(normalized.substringAfter(' '))
            if (expression.any { it.isDigit() } && expression.any { it in "+-*/^()" }) return expression
        }
        return null
    }

    private fun normalizeMath(value: String): String = normalize(value)
        .replace(Regex("\\bvezes\\b"), "*")
        .replace(Regex("\\bmultiplicado por\\b"), "*")
        .replace(Regex("\\bdividido por\\b"), "/")
        .replace(Regex("\\bmais\\b"), "+")
        .replace(Regex("\\bmenos\\b"), "-")
        .replace(Regex("\\bpotencia de\\b"), "^")
        .replace(",", ".")
        .replace(Regex("[^0-9.+*/^()\\- ]"), "")
        .trim()

    private fun isTimeRequest(value: String): Boolean =
        value in setOf("que horas sao", "hora", "horas", "horario atual", "diga a hora atual", "qual e o horario agora", "current time")

    private fun isDateRequest(value: String): Boolean =
        value in setOf("que dia e hoje", "qual a data de hoje", "data de hoje", "data", "diga a data", "today date", "today's date")

    private fun startsWithQuestion(value: String): Boolean =
        listOf("o que ", "que e ", "quem ", "como ", "por que ", "porque ", "quando ", "onde ", "qual ", "quanto ", "me explique ", "explique ", "tell me ", "what ", "who ", "why ", "how ").any { value.startsWith(it) }

    private fun cleanAppName(value: String): String =
        value.trim().trimEnd('.', '?', '!', ',')
            .replace(Regex("(?i)^(?:o|a|os|as|app|aplicativo|programa)\\s+"), "")
            .trim()

    private fun formatNumber(value: Double): String {
        val format = DecimalFormat("0.##########", DecimalFormatSymbols(Locale.US))
        return format.format(value)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun helpText(): String =
        "J.A.R.V.I.S. local: abrir apps/sites, pesquisar, calcular, hora/data, volume/mídia, Configurações, voltar/início/notificações, captura de tela, notas, automação e até 100 macros aprendidas. Ensine uma macro com: ensine frase => ação. O classificador tem 2.678 unidades ocultas, mas não é um LLM. Voz usa reconhecimento offline quando instalado; pedidos desconhecidos abrem uma pesquisa no navegador. Nenhuma API de IA é chamada."

    private class MathParser(private val source: String) {
        private var index = 0

        fun evaluate(): Double {
            val value = expression()
            whitespace()
            require(index == source.length && value.isFinite()) { "expressão inválida" }
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                whitespace()
                value = when {
                    take('+') -> value + term()
                    take('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                whitespace()
                value = when {
                    take('*') -> value * power()
                    take('/') -> {
                        val divisor = power()
                        require(divisor != 0.0) { "divisão por zero" }
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            var value = unary()
            whitespace()
            if (take('^')) value = value.pow(power())
            return value
        }

        private fun unary(): Double {
            whitespace()
            return when {
                take('+') -> unary()
                take('-') -> -unary()
                take('(') -> {
                    val value = expression()
                    whitespace()
                    require(take(')')) { "parêntese não fechado" }
                    value
                }
                else -> number()
            }
        }

        private fun number(): Double {
            whitespace()
            val start = index
            var dots = 0
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                if (source[index] == '.') dots++
                require(dots <= 1) { "número inválido" }
                index++
            }
            require(index > start) { "esperado número" }
            return source.substring(start, index).toDouble()
        }

        private fun whitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun take(char: Char): Boolean {
            if (index < source.length && source[index] == char) {
                index++
                return true
            }
            return false
        }
    }
}
