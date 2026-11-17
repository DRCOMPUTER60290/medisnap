package com.example.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SovereignWebDavSyncClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun buildTargetUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "Conformité ANSSI : seul le protocole chiffré HTTPS est autorisé pour un serveur WebDAV."
        }
        return if (trimmed.endsWith(".medisnap", ignoreCase = true) ||
            trimmed.endsWith(".json", ignoreCase = true)
        ) {
            trimmed
        } else {
            "$trimmed/$DEFAULT_BACKUP_FILENAME"
        }
    }

    suspend fun uploadEncryptedArchive(
        baseUrl: String,
        username: String,
        password: String,
        envelopeJson: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = buildTargetUrl(baseUrl)
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .put(envelopeJson.toRequestBody("application/json; charset=utf-8".toMediaType()))

            if (username.isNotBlank() || password.isNotBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(username.trim(), password))
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Échec WebDAV (${response.code}) : ${response.message}")
                }
                targetUrl
            }
        }
    }

    suspend fun downloadEncryptedArchive(
        baseUrl: String,
        username: String,
        password: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val targetUrl = buildTargetUrl(baseUrl)
            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .get()

            if (username.isNotBlank() || password.isNotBlank()) {
                requestBuilder.header("Authorization", Credentials.basic(username.trim(), password))
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Archive introuvable ou accès refusé (${response.code}).")
                }
                response.body?.string() ?: throw IllegalStateException("Réponse vide du serveur WebDAV.")
            }
        }
    }

    companion object {
        const val DEFAULT_BACKUP_FILENAME = "medisnap_backup.medisnap"
    }
}
