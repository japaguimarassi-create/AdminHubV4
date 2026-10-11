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
        return "O Google recusou a reautenticação (erro 16). Uma causa comum é o cliente OAuth Android não corresponder à assinatura. No mesmo projeto Google Cloud do Client ID Web, cadastre um cliente Android com pacote '" + context.packageName + "' e SHA-1 '" + sha1 + "'. O setServerClientId deve continuar usando o Client ID Web. Depois reinstale a build com a assinatura correspondente e tente novamente."
    }
}
