package com.jarvis.assistant

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object JarvisAuthRepository {
    suspend fun signInWithGoogle(activity: Activity): JarvisAccount {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        val endpoint = BuildConfig.JARVIS_AUTH_ENDPOINT.trim()
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

        require(clientId.endsWith(".apps.googleusercontent.com")) {
            "O Client ID Web do Google está ausente ou malformado no build."
        }
        require(publishableKey.startsWith("sb_publishable_")) {
            "A chave pública do Supabase não está configurada neste build."
        }
        require(endpoint.startsWith("https://")) {
            "O endpoint HTTPS do login J.A.R.V.I.S. não está configurado."
        }

        val serverNonce = requestServerNonce(endpoint, publishableKey)
        val result = acquireGoogleCredential(activity, clientId, serverNonce)
        val credential = result.credential as? CustomCredential
            ?: throw IllegalStateException("O Google não retornou uma credencial compatível.")
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("O provedor retornou um tipo de credencial inesperado.")
        }

        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val profile = validateTokenWithBackend(
            endpoint = endpoint,
            idToken = google.idToken,
            publishableKey = publishableKey
        )
        return JarvisAccount(
            email = profile.getString("email"),
            displayName = profile.optString("name", google.displayName ?: google.id),
            pictureUrl = profile.optString("picture", google.profilePictureUri?.toString().orEmpty()),
            isOwner = profile.optBoolean("is_owner", false)
        )
    }

    private suspend fun acquireGoogleCredential(
        activity: Activity,
        clientId: String,
        serverNonce: String
    ): GetCredentialResponse {
        val manager = CredentialManager.create(activity)
        val authorizedRequest = googleIdRequest(clientId, serverNonce, true)
        try {
            return manager.getCredential(context = activity, request = authorizedRequest)
        } catch (firstError: Exception) {
            if (JarvisCredentialErrorClassifier.isUserCancellation(firstError)) throw firstError
            if (!JarvisCredentialErrorClassifier.shouldTryAlternative(firstError)) {
                throw IllegalStateException(
                    JarvisOAuthDiagnostics.credentialFailure(activity, firstError),
                    firstError
                )
            }
        }

        val allAccountsRequest = googleIdRequest(clientId, serverNonce, false)
        try {
            return manager.getCredential(context = activity, request = allAccountsRequest)
        } catch (secondError: Exception) {
            if (JarvisCredentialErrorClassifier.isUserCancellation(secondError)) throw secondError
            if (!JarvisCredentialErrorClassifier.shouldTryAlternative(secondError)) {
                throw IllegalStateException(
                    JarvisOAuthDiagnostics.credentialFailure(activity, secondError),
                    secondError
                )
            }
        }

        val buttonOption = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(serverNonce)
            .build()
        val buttonRequest = GetCredentialRequest.Builder()
            .addCredentialOption(buttonOption)
            .build()
        return try {
            manager.getCredential(context = activity, request = buttonRequest)
        } catch (finalError: Exception) {
            if (JarvisCredentialErrorClassifier.isUserCancellation(finalError)) throw finalError
            throw IllegalStateException(
                JarvisOAuthDiagnostics.credentialFailure(activity, finalError),
                finalError
            )
        }
    }

    private fun googleIdRequest(
        clientId: String,
        nonce: String,
        authorizedOnly: Boolean
    ): GetCredentialRequest {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
    }

    private suspend fun requestServerNonce(
        endpoint: String,
        publishableKey: String
    ): String = withContext(Dispatchers.IO) {
        val response = postJsonBlocking(
            endpoint,
            publishableKey,
            JSONObject().put("action", "nonce")
        )
        if (response.code !in 200..299 || !response.body.optBoolean("ok", false)) {
            val detail = response.body.optString("error", "O servidor não conseguiu preparar o login seguro.")
            throw IllegalStateException(detail.take(200))
        }
        val nonce = response.body.optString("nonce").trim()
        if (nonce.length < 32 || nonce.length > 256 || !nonce.matches(Regex("[A-Za-z0-9_-]+"))) {
            throw IllegalStateException("O servidor retornou um nonce inválido. Tente novamente.")
        }
        nonce
    }

    private suspend fun validateTokenWithBackend(
        endpoint: String,
        idToken: String,
        publishableKey: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val response = postJsonBlocking(
            endpoint,
            publishableKey,
            JSONObject()
                .put("action", "verify")
                .put("id_token", idToken)
        )
        if (response.code !in 200..299 || !response.body.optBoolean("ok", false)) {
            val detail = response.body.optString("error", "O servidor não validou a Conta Google.")
            throw IllegalStateException(detail.take(220))
        }
        response.body
    }

    private fun postJsonBlocking(
        endpoint: String,
        publishableKey: String,
        payload: JSONObject
    ): HttpJsonResponse {
        val connection = URL(endpoint).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("apikey", publishableKey)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(bytes) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bodyText = if (stream == null) "" else stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val body = try {
                JSONObject(bodyText)
            } catch (_: Exception) {
                JSONObject()
            }
            return HttpJsonResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpJsonResponse(
        val code: Int,
        val body: JSONObject
    )
}
