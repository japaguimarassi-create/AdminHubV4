package com.jarvis.assistant

import java.text.Normalizer
import java.util.Locale

enum class JarvisIntentKind { LOCAL_COMMAND, WEB_SEARCH, NATURAL_QUESTION, UNKNOWN }

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
    private val searchPattern = Regex("^(?:pesquise(?: na web| na internet)?|pesquisar(?: na web| na internet)?|procure|procurar|busque|buscar|google|pesquisa na web|search)\\s+.+$")
    private val questionStarts = listOf(
        "o que ", "quem ", "como ", "por que ", "porque ", "quando ", "onde ", "qual ",
        "quais ", "quanto ", "quantos ", "quantas ", "me explique ", "me explica ",
        "explique ", "conte sobre ", "me fale sobre ", "tell me ", "what ", "who ", "why ", "how "
    )

    fun normalizeTranscript(value: String): String {
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        normalized = normalized.replace(
            Regex("^(?:j\\.?\\s*a\\.?\\s*r\\.?\\s*v\\.?\\s*i\\.?\\s*s\\.?|jarvis)[,;:\\s-]+"),
            ""
        )
        return normalized.replace(Regex("[.!]+$"), "").trim()
    }

    fun classify(raw: String): JarvisIntent {
        val hadQuestionMark = raw.trim().endsWith("?")
        val cleaned = normalizeTranscript(raw)
        if (cleaned.isBlank()) return JarvisIntent(JarvisIntentKind.UNKNOWN, "", true, "A transcrição veio vazia.")
        if (searchPattern.matches(cleaned)) {
            return JarvisIntent(JarvisIntentKind.WEB_SEARCH, cleaned, false, "Pesquisa pedida explicitamente.")
        }
        if (localCommandPatterns.any { it.matches(cleaned) }) {
            val caution = cleaned.startsWith("quando abrir ") ||
                cleaned.startsWith("ao abrir ") ||
                cleaned.startsWith("assim que abrir ") ||
                cleaned.startsWith("quando o app ") ||
                listOf("digite ", "toque ", "clique ", "envie ", "mande ", "apague ", "delete ", "compre ", "pague ", "publique ", "compartilhe ").any { cleaned.startsWith(it) }
            return JarvisIntent(
                JarvisIntentKind.LOCAL_COMMAND,
                cleaned,
                caution,
                if (caution) "A ação pode alterar outro aplicativo." else "Comando local reconhecido."
            )
        }
        if (hadQuestionMark || questionStarts.any { cleaned.startsWith(it) }) {
            return JarvisIntent(JarvisIntentKind.NATURAL_QUESTION, cleaned, false, "Pergunta em linguagem natural.")
        }
        return JarvisIntent(JarvisIntentKind.UNKNOWN, cleaned, true, "Não há correspondência suficiente para executar com segurança.")
    }
}
