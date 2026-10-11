package com.jarvis.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

object JarvisOAuthDiagnostics {
    @Suppress("DEPRECATION")
    fun signingCertificateSha1(context: Context): String? {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val signature = signatures?.firstOrNull() ?: return null
            val digest = MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
            digest.joinToString(":") { String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF) }
        } catch (_: Exception) {
            null
        }
    }

    fun accountReauthGuidance(context: Context): String {
        val sha1 = signingCertificateSha1(context) ?: "não foi possível ler no dispositivo"
        return "O Google recusou a reautenticação. No mesmo projeto Google Cloud do Client ID Web, crie ou confira um cliente OAuth do tipo Android com pacote '" +
            context.packageName + "' e SHA-1 '" + sha1 + "'. O app deve continuar usando o Client ID Web como serverClientId e audience do token."
    }

    fun credentialFailure(context: Context, error: Throwable): String {
        if (JarvisCredentialErrorClassifier.isUserCancellation(error)) {
            return "Login cancelado. Nenhuma conta foi conectada."
        }
        val base = accountReauthGuidance(context)
        val detail = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull()
            ?.take(140)
        return if (detail.isNullOrBlank()) base else base + "\nDetalhe do Google: " + detail
    }
}
