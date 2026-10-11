package com.jarvis.assistant

import android.app.Activity
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection

object JarvisAuthRepository {
    suspend fun signInWithGoogle(activity: Activity): JarvisAccount {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        val endpoint = BuildConfig.JARVIS_AUTH_ENDPOINT.trim()
        require(clientId.isNotBlank()) {
            "O login Google ainda não foi conectado ao projeto OAuth. Configure JARVIS_GOOGLE_WEB_CLIENT_ID no GitHub."
        }
        require(endpoint.startsWith("https://")) {
            "O backend global ainda não foi configurado. Configure JARVIS_AUTH_ENDPOINT depois de publicar a função segura do J.A.R.V.I.S."
        }

        val nonceBytes = ByteArray(32)
        SecureRandom().nextBytes(nonceBytes)
        val nonce = Base64.encodeToString(
            nonceBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val option = GetSignInWithGoogleOption.Builder(clientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = request
        )
        val credential = result.credential as? CustomCredential
            ?: throw IllegalStateException("O provedor não retornou uma credencial Google compatível.")
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("O tipo de credencial retornado não é um token de identidade Google.")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val profile = validateTokenWithBackend(
            endpoint = endpoint,
            idToken = google.idToken,
            nonce = nonce
        )
        return JarvisAccount(
            email = profile.getString("email"),
            displayName = profile.optString("name", google.displayName ?: google.id),
            pictureUrl = profile.optString("picture", google.profilePictureUri?.toString().orEmpty()),
            isOwner = profile.optBoolean("is_owner", false)
        )
    }

    private suspend fun validateTokenWithBackend(
        endpoint: String,
        idToken: String,
        nonce: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val requestBody = JSONObject()
                .put("id_token", idToken)
                .put("nonce", nonce)
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(requestBody) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                JSONObject()
            }
            if (code !in 200..299 || !json.optBoolean("ok", false)) {
                val safeMessage = json.optString("error", "O servidor não validou a conta Google.")
                throw IllegalStateException(safeMessage.take(220))
            }
            json
        } finally {
            connection.disconnect()
        }
    }
}
