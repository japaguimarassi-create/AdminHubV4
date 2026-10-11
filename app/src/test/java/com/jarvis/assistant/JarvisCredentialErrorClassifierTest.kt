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
    }

    @Test
    fun recognizesWrappedReauthenticationFailure() {
        assertTrue(JarvisCredentialErrorClassifier.isAccountReauthFailure(
            IllegalStateException("Credential failure", IllegalArgumentException("Account reauth failed"))
        ))
    }

    @Test
    fun doesNotTreatUserCancellationAsReauthenticationFailure() {
        assertFalse(JarvisCredentialErrorClassifier.isAccountReauthFailure(
            IllegalStateException("[16] Cancelled by user.")
        ))
    }
}
