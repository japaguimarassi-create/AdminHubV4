package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

data class JarvisKnowledgeFact(
    val id: String,
    val category: String,
    val term: String,
    val question: String,
    val answer: String,
    val aliases: List<String>
)

data class JarvisKnowledgeHit(val fact: JarvisKnowledgeFact, val score: Double)

object JarvisKnowledgeIndex {
    private val stopWords = setOf(
        "o", "a", "os", "as", "um", "uma", "de", "do", "da", "dos", "das", "e", "ou",
        "que", "qual", "quais", "como", "quando", "onde", "por", "para", "me", "sobre",
        "explique", "defina", "diga", "quero", "saber", "the", "is", "what", "explain"
    )

    fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun search(query: String, facts: List<JarvisKnowledgeFact>): JarvisKnowledgeHit? {
        val normalized = normalize(query)
        if (normalized.isBlank()) return null

        facts.forEach { fact ->
            if (fact.aliases.any { normalize(it) == normalized } || normalize(fact.question) == normalized) {
                return JarvisKnowledgeHit(fact, 1.0)
            }
        }

        val queryTokens = meaningfulTokens(normalized)
        if (queryTokens.isEmpty()) return null
        val candidates = facts.mapNotNull { fact ->
            val searchable = (fact.aliases + fact.term + fact.question)
                .flatMap { meaningfulTokens(normalize(it)) }.toSet()
            val overlap = queryTokens.count { it in searchable }
            if (overlap == 0) null else JarvisKnowledgeHit(fact, overlap.toDouble() / queryTokens.size.toDouble())
        }.sortedByDescending { it.score }

        val best = candidates.firstOrNull() ?: return null
        if (queryTokens.size == 1) {
            val term = normalize(best.fact.term)
            if (term != normalized && best.fact.aliases.none { normalize(it) == normalized }) return null
        } else if (best.score < 0.72) {
            return null
        }
        val runnerUp = candidates.drop(1).firstOrNull()
        if (runnerUp != null && queryTokens.size > 1 &&
            best.score - runnerUp.score < 0.08 && best.fact.answer != runnerUp.fact.answer) return null
        return best
    }

    private fun meaningfulTokens(value: String): List<String> =
        value.split(Regex("\\s+")).filter { it.isNotBlank() && it !in stopWords }
}

object JarvisKnowledgeBase {
    private const val ASSET_NAME = "jarvis_knowledge.json"
    @Volatile private var cache: List<JarvisKnowledgeFact>? = null

    fun facts(context: Context): List<JarvisKnowledgeFact> {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: load(context.applicationContext).also { cache = it }
        }
    }

    fun count(context: Context): Int = facts(context).size

    fun lookup(context: Context, query: String): JarvisKnowledgeFact? =
        JarvisKnowledgeIndex.search(query, facts(context))?.fact

    fun answer(context: Context, query: String): String? {
        val normalized = JarvisKnowledgeIndex.normalize(query)
        if (normalized.isBlank()) return null
        if (Regex("^(?:pesquise|pesquisar|procure|procurar|busque|buscar|google|pesquisa na web|search)\\b")
                .containsMatchIn(normalized)) return null
        if (Regex("[+*/^=()]").containsMatchIn(query)) return null
        val hit = JarvisKnowledgeIndex.search(query, facts(context)) ?: return null
        return "Base offline • " + hit.fact.category + "\n" + hit.fact.answer
    }

    private fun load(context: Context): List<JarvisKnowledgeFact> {
        return try {
            val raw = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val array = JSONObject(raw).optJSONArray("facts") ?: JSONArray()
            val result = mutableListOf<JarvisKnowledgeFact>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val aliasesArray = item.optJSONArray("aliases") ?: JSONArray()
                val aliases = mutableListOf<String>()
                for (j in 0 until aliasesArray.length()) {
                    val alias = aliasesArray.optString(j).trim()
                    if (alias.isNotBlank()) aliases.add(alias)
                }
                val answer = item.optString("answer").trim()
                val term = item.optString("term").trim()
                if (answer.isBlank() || term.isBlank()) continue
                result.add(JarvisKnowledgeFact(
                    id = item.optString("id", "fact-" + i),
                    category = item.optString("category", "Conhecimento geral"),
                    term = term,
                    question = item.optString("question", "O que é " + term + "?"),
                    answer = answer,
                    aliases = aliases + term
                ))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
