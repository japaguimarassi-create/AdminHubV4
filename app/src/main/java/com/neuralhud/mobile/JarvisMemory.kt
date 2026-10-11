package com.neuralhud.mobile

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

data class LearnedCommand(val phrase: String, val command: String)

object JarvisMemory {
    private const val PREFS = "jarvis_learned_commands"
    private const val KEY_COMMANDS = "commands"
    private const val MAX_COMMANDS = 100

    fun teach(context: Context, raw: String): String? {
        val lesson = parseTeachingCommand(raw) ?: return null
        val phrase = normalize(lesson.phrase)
        if (phrase.length !in 2..120 || lesson.command.length > 500) {
            return "Use uma frase de 2 a 120 caracteres e uma ação de até 500 caracteres."
        }
        if (normalize(lesson.command) == phrase) {
            return "A frase e a ação não podem ser iguais."
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val commands = load(prefs.getString(KEY_COMMANDS, "{}"))
        if (!commands.has(phrase) && commands.length() >= MAX_COMMANDS) {
            return "A memória local atingiu o limite de $MAX_COMMANDS comandos."
        }
        commands.put(phrase, lesson.command)
        Thread({
            try {
                val learnedIntent = JarvisLocalBrain.classify(context.applicationContext, lesson.command).intent
                JarvisLocalBrain.learn(context.applicationContext, lesson.phrase, learnedIntent)
            } catch (_: Exception) {}
        }, "jarvis-local-learning").apply { isDaemon = true; start() }
        prefs.edit().putString(KEY_COMMANDS, commands.toString()).apply()
        return "Aprendi localmente: “${lesson.phrase}” executa “${lesson.command}”. Sem API."
    }

    fun resolve(context: Context, phrase: String): String? {
        val key = normalize(phrase)
        if (key.isBlank()) return null
        return load(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COMMANDS, "{}"))
            .optString(key)
            .takeIf { it.isNotBlank() }
    }

    fun count(context: Context): Int {
        return load(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_COMMANDS, "{}")).length()
    }

    fun parseTeachingCommand(raw: String): LearnedCommand? {
        val text = raw.trim()
        if (text.isBlank()) return null

        val arrows = Regex("""(?is)^(?:ensine|aprenda|memorize|guarde)\s+(?:que\s+)?(.+?)\s*(?:=>|->|::)\s*(.+)$""")
        val arrow = arrows.matchEntire(text)
        if (arrow != null) {
            val phrase = clean(arrow.groupValues[1])
            val command = clean(arrow.groupValues[2])
            if (phrase.isNotBlank() && command.isNotBlank()) return LearnedCommand(phrase, command)
        }

        val natural = Regex("""(?is)^(?:ensine|aprenda|memorize)\s+que\s+quando eu disser\s+(.+?),\s*(?:execute|faça|faca|abra|digite|pesquise|procure|busque)\s+(.+)$""")
        val match = natural.matchEntire(text)
        if (match != null) {
            val phrase = clean(match.groupValues[1])
            val command = clean(match.groupValues[2])
            if (phrase.isNotBlank() && command.isNotBlank()) return LearnedCommand(phrase, command)
        }
        return null
    }

    private fun load(raw: String?): JSONObject {
        return try {
            JSONObject(raw ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun clean(value: String): String {
        return value.trim().trim('"', '\'', '“', '”', '‘', '’', ' ', '\t', '\n', ',')
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
