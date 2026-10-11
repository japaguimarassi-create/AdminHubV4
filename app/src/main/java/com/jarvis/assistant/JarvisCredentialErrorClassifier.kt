package com.jarvis.assistant

object JarvisCredentialErrorClassifier {
    fun isAccountReauthFailure(error: Throwable): Boolean {
        val visited = HashSet<Throwable>()
        var current: Throwable? = error
        while (current != null && visited.add(current)) {
            val message = current.message.orEmpty()
            if (message.contains("Account reauth failed", ignoreCase = true)) return true
            current = current.cause
        }
        return false
    }
}
