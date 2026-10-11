package com.jarvis.assistant

object JarvisCredentialErrorClassifier {
    fun isAccountReauthFailure(error: Throwable): Boolean {
        return anyCause(error) { current ->
            current.message.orEmpty().contains("Account reauth failed", ignoreCase = true) ||
                (current.message.orEmpty().contains("[16]", ignoreCase = true) &&
                    !current.message.orEmpty().contains("cancel", ignoreCase = true))
        }
    }

    fun isNoCredentialFailure(error: Throwable): Boolean {
        return anyCause(error) { current ->
            current.javaClass.simpleName.contains("NoCredentialException", ignoreCase = true) ||
                current.message.orEmpty().contains("no credential", ignoreCase = true) ||
                current.message.orEmpty().contains("no credentials", ignoreCase = true)
        }
    }

    fun shouldTryAlternative(error: Throwable): Boolean =
        isAccountReauthFailure(error) || isNoCredentialFailure(error)

    fun isUserCancellation(error: Throwable): Boolean {
        return anyCause(error) { current ->
            current.javaClass.simpleName.contains("CancellationException", ignoreCase = true) ||
                current.javaClass.simpleName.contains("CanceledException", ignoreCase = true) ||
                current.message.orEmpty().contains("cancelled by user", ignoreCase = true) ||
                current.message.orEmpty().contains("canceled by user", ignoreCase = true)
        }
    }

    private fun anyCause(error: Throwable, predicate: (Throwable) -> Boolean): Boolean {
        val visited = HashSet<Throwable>()
        var current: Throwable? = error
        while (current != null && visited.add(current)) {
            if (predicate(current)) return true
            current = current.cause
        }
        return false
    }
}
