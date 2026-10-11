package com.jarvis.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class JarvisChatLine(val role: String, val text: String, val time: Long)

data class JarvisConversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val lines: List<JarvisChatLine>
)

object JarvisConversationStore {
    private const val PREFS = "jarvis_conversations"
    private const val KEY_DATA = "data"
    private const val KEY_CURRENT = "current_id"
    private const val MAX_CHATS = 40
    private const val MAX_LINES = 120

    @Synchronized
    fun currentId(context: Context): String {
        val prefs = prefs(context)
        val current = prefs.getString(KEY_CURRENT, "").orEmpty()
        if (current.isNotBlank() && all(context).any { it.id == current }) return current
        return create(context)
    }

    @Synchronized
    fun create(context: Context): String {
        val id = UUID.randomUUID().toString()
        val items = read(context)
        items.put(JSONObject()
            .put("id", id)
            .put("title", "Nova conversa")
            .put("updated_at", System.currentTimeMillis())
            .put("lines", JSONArray()))
        trim(items)
        prefs(context).edit().putString(KEY_DATA, items.toString()).putString(KEY_CURRENT, id).apply()
        return id
    }

    @Synchronized
    fun select(context: Context, id: String): Boolean {
        if (all(context).none { it.id == id }) return false
        prefs(context).edit().putString(KEY_CURRENT, id).apply()
        return true
    }

    @Synchronized
    fun current(context: Context): JarvisConversation? {
        val id = currentId(context)
        return all(context).firstOrNull { it.id == id }
    }

    @Synchronized
    fun all(context: Context): List<JarvisConversation> {
        val data = read(context)
        val result = mutableListOf<JarvisConversation>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val linesArray = item.optJSONArray("lines") ?: JSONArray()
            val lines = mutableListOf<JarvisChatLine>()
            for (j in 0 until linesArray.length()) {
                val line = linesArray.optJSONObject(j) ?: continue
                lines.add(JarvisChatLine(
                    role = line.optString("role", "assistant"),
                    text = line.optString("text", ""),
                    time = line.optLong("time", 0L)
                ))
            }
            result.add(JarvisConversation(
                id = item.optString("id"),
                title = item.optString("title", "Conversa"),
                updatedAt = item.optLong("updated_at", 0L),
                lines = lines
            ))
        }
        return result.sortedByDescending { it.updatedAt }
    }

    @Synchronized
    fun appendExchange(context: Context, userText: String, assistantText: String) {
        val id = currentId(context)
        val items = read(context)
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.optString("id") != id) continue
            val lines = item.optJSONArray("lines") ?: JSONArray()
            val now = System.currentTimeMillis()
            lines.put(JSONObject().put("role", "user").put("text", userText).put("time", now))
            lines.put(JSONObject().put("role", "assistant").put("text", assistantText).put("time", now + 1))
            while (lines.length() > MAX_LINES) lines.remove(0)
            item.put("lines", lines)
            item.put("updated_at", now)
            if (item.optString("title") == "Nova conversa") {
                val title = userText.replace(Regex("\\s+"), " ").trim().take(42)
                item.put("title", if (title.isBlank()) "Nova conversa" else title)
            }
            break
        }
        trim(items)
        prefs(context).edit().putString(KEY_DATA, items.toString()).apply()
    }

    @Synchronized
    fun clearAll(context: Context) {
        prefs(context).edit().remove(KEY_DATA).remove(KEY_CURRENT).apply()
    }

    private fun read(context: Context): JSONArray =
        try { JSONArray(prefs(context).getString(KEY_DATA, "[]")) } catch (_: Exception) { JSONArray() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun trim(items: JSONArray) {
        while (items.length() > MAX_CHATS) items.remove(0)
    }
}
