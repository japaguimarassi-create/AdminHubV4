package com.jarvis.assistant

import android.content.Context

data class JarvisAccount(
    val email: String,
    val displayName: String,
    val pictureUrl: String,
    val isOwner: Boolean
)

object JarvisAuthState {
    private const val PREFS = "jarvis_identity"
    private const val KEY_MODE = "mode"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"
    private const val KEY_PICTURE = "picture"
    private const val KEY_OWNER = "owner"

    fun mode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_MODE, "signed_out") ?: "signed_out"

    fun hasAccess(context: Context): Boolean = mode(context) in setOf("google", "local")

    fun isGoogle(context: Context): Boolean = mode(context) == "google"

    fun isOwner(context: Context): Boolean =
        isGoogle(context) && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OWNER, false)

    fun currentAccount(context: Context): JarvisAccount {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JarvisAccount(
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            displayName = prefs.getString(KEY_NAME, if (mode(context) == "local") "Modo local" else "").orEmpty(),
            pictureUrl = prefs.getString(KEY_PICTURE, "").orEmpty(),
            isOwner = isOwner(context)
        )
    }

    fun saveGoogle(context: Context, account: JarvisAccount) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, "google")
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_NAME, account.displayName)
            .putString(KEY_PICTURE, account.pictureUrl)
            .putBoolean(KEY_OWNER, account.isOwner)
            .apply()
    }

    fun saveLocal(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .clear()
            .putString(KEY_MODE, "local")
            .putString(KEY_NAME, "Modo local")
            .putBoolean(KEY_OWNER, false)
            .apply()
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
