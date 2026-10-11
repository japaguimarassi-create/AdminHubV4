package com.jarvis.assistant

import java.text.Normalizer
import java.util.Locale

data class ParsedAppSequence(val appLabel: String, val followUp: String)

object JarvisAppSequenceParser {
    private val commandPrefix = Regex(
        "^(?:(?:eu quero|quero|gostaria de|por favor) )?(?:abra|abrir|abre|abri|inicie|iniciar|inicialize|inicializar|execute|executar|rode|rodar|lance|lancar|acessar|acesse|visitar|visite|ir para|va para|vai para|quero usar|launch|open|start|go to)\\s+"
    )
    private val leadingArticle = Regex("^(?:o|a|os|as|um|uma|app|aplicativo|programa)\\s+")
    private val aliases = mapOf(
        "wa" to "whatsapp",
        "zap" to "whatsapp",
        "yt" to "youtube",
        "insta" to "instagram",
        "maps" to "google maps",
        "mapas" to "google maps",
        "navegador" to "chrome",
        "browser" to "chrome",
        "musica" to "spotify",
        "music" to "spotify"
    )

    fun parse(command: String, installedLabels: List<String>): ParsedAppSequence? {
        val normalized = normalize(command)
        val withoutVerb = commandPrefix.replaceFirst(normalized, "").trim()
        val body = leadingArticle.replaceFirst(withoutVerb, "").trim()
        if (body.isBlank()) return null

        val candidates = mutableListOf<Pair<String, String>>()
        installedLabels.map { normalize(it) to it.trim() }
            .filter { it.first.isNotBlank() }
            .forEach { (label, actual) ->
                candidates.add(label to actual)
                aliases.forEach { (alias, canonical) ->
                    if (canonical == label) candidates.add(alias to actual)
                }
            }

        val candidate = candidates.distinct().sortedByDescending { it.first.length }
            .firstOrNull { (spokenLabel, _) -> body.startsWith(spokenLabel + " ") || body == spokenLabel }
            ?: return null
        val tail = body.drop(candidate.first.length).trim()
        if (tail.isBlank()) return null

        val connector = Regex("^(?:e\\s+depois|e\\s+entao|depois|entao|e|se)\\s+(.+)$").matchEntire(tail)
            ?: return null
        val followUp = connector.groupValues[1].trim()
        if (followUp.isBlank()) return null
        return ParsedAppSequence(candidate.second, followUp)
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
