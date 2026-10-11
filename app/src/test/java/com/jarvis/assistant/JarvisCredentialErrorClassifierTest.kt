package com.jarvis.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisCredentialErrorClassifierTest {
    @Test
    fun recognizesReauthenticationFailure() {
        assertTrue(JarvisCredentialErrorClassifier.isAccountReauthFailure(
            IllegalStateException("[16] Account reauth failed.")
        ))
        assertTrue(JarvisCredentialErrorClassifier.shouldTryAlternative(
            IllegalStateException("[16] Account reauth failed.")
        ))
    }

    @Test
    fun recognizesWrappedReauthenticationFailure() {
        assertTrue(JarvisCredentialErrorClassifier.isAccountReauthFailure(
            IllegalStateException("Credential failure", IllegalArgumentException("Account reauth failed"))
        ))
    }

    @Test
    fun recognizesNoCredentialMessagesForFallback() {
        assertTrue(JarvisCredentialErrorClassifier.shouldTryAlternative(
            IllegalStateException("No credential available from provider")
        ))
    }

    @Test
    fun doesNotTreatUserCancellationAsReauthenticationFailure() {
        val error = IllegalStateException("[16] Cancelled by user.")
        assertFalse(JarvisCredentialErrorClassifier.isAccountReauthFailure(error))
        assertTrue(JarvisCredentialErrorClassifier.isUserCancellation(error))
        assertFalse(JarvisCredentialErrorClassifier.shouldTryAlternative(error))
    }
}
