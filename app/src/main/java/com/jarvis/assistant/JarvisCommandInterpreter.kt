package com.jarvis.assistant

import java.text.Normalizer
import java.util.Locale

enum class JarvisIntentKind {
    LOCAL_COMMAND,
    WEB_SEARCH,
    NATURAL_QUESTION,
    UNKNOWN
}

data class JarvisIntent(
    val kind: JarvisIntentKind,
    val cleanedText: String,
    val needsConfirmation: Boolean,
    val reason: String
)

object JarvisCommandInterpreter {
    private val localCommandPatterns = listOf(
        Regex("^(?:ajuda|help|comandos|o que voce sabe fazer|quais sao suas funcoes)$"),
        Regex("^(?:que horas sao|hora|horas|horario atual|qual e o horario agora|qual a data de hoje|data de hoje|que dia e hoje|data)$"),
        Regex("^(?:calcule|calcular|quanto e|quanto da|resolva|compute|calculate)\\b.*"),
        Regex("^(?:abra|abrir|abre|inicie|iniciar|acesse|acessar|visite|visitar|va para|ir para|launch|open|start)\\b.+"),
        Regex("^(?:aumentar volume|diminuir volume|volume para cima|volume para baixo|silenciar|mutar|pausar musica|tocar musica|play|pause)$"),
        Regex("^(?:voltar|volte|tela inicial|home|notificacoes|aplicativos recentes|capturar tela|tirar print|configuracoes|abrir configuracoes|abrir wifi|abrir bluetooth)$"),
        Regex("^(?:anote|anotar|lembre que|lembre-me de|mostrar nota|ler minha nota)\\b.*"),
        Regex("^(?:quando abrir|ao abrir|assim que abrir|quando o app)\\b.+"),
        Regex("^(?:ensine|ensinar)\\b.+=>.+")
    )
    private val searchPattern = Regex("^(?:pesquise|pesquisar|procure|procurar|busque|buscar|google|pesquisa na web|pesquise na internet|search)\\s+.+$")
    private val questionStarts = listOf(
        "o que ", "quem ", "como ", "por que ", "porque ", "quando ", "onde ", "qual ",
        "quais ", "quanto ", "quantos ", "quantas ", "me explique ", "explique ", "tell me ",
        "what ", "who ", "why ", "how "
    )
    private val actionWords = listOf(
        "toque ", "clique ", "digite ", "envie ", "mande ", "apague ", "delete ", "compre ",
        "pague ", "publique ", "compartilhe ", "confirme ", "aceite ", "instale ", "desinstale "
    )

    fun normalizeTranscript(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized
            .removePrefix("jarvis ")
            .removePrefix("j a r v i s ")
            .replace(Regex("[.!?]+$"), "")
            .trim()
    }

    fun classify(raw: String): JarvisIntent {
        val cleaned = normalizeTranscript(raw)
        if (cleaned.isBlank()) {
            return JarvisIntent(JarvisIntentKind.UNKNOWN, "", true, "A transcrição veio vazia.")
        }
        if (searchPattern.matches(cleaned)) {
            return JarvisIntent(JarvisIntentKind.WEB_SEARCH, cleaned, false, "Pesquisa pedida explicitamente.")
        }
        if (localCommandPatterns.any { it.matches(cleaned) }) {
            val caution = actionWords.any { cleaned.startsWith(it) } ||
                cleaned.startsWith("quando abrir ") ||
                cleaned.startsWith("ao abrir ") ||
                cleaned.startsWith("assim que abrir ") ||
                cleaned.startsWith("quando o app ")
            return JarvisIntent(
                JarvisIntentKind.LOCAL_COMMAND,
                cleaned,
                caution,
                if (caution) "A ação pode alterar outro aplicativo." else "Comando local reconhecido."
            )
        }
        if (cleaned.endsWith("?") || questionStarts.any { cleaned.startsWith(it) }) {
            return JarvisIntent(JarvisIntentKind.NATURAL_QUESTION, cleaned, false, "Pergunta em linguagem natural.")
        }
        return JarvisIntent(JarvisIntentKind.UNKNOWN, cleaned, true, "Não há correspondência suficiente para executar com segurança.")
    }
}
