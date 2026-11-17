package com.example.data.sync

import android.util.Base64
import com.example.data.model.Medication
import com.example.data.model.MedicationLog
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class SyncPayload(
    val encryptedData: String,
    val iv: String,
    val salt: String,
    val timestamp: Long,
    val deviceId: String,
    val version: Int = CryptoSyncEngine.CURRENT_VERSION,
    val kdf: String = CryptoSyncEngine.KDF_ALGORITHM,
    val iterations: Int = CryptoSyncEngine.DEFAULT_ITERATIONS,
    val sha256Digest: String = "",
    val medicationCount: Int = 0,
    val logCount: Int = 0
)

data class PasswordStrength(
    val score: Int, // 0..4
    val label: String,
    val isAnssiRecommended: Boolean,
    val guidance: String
)

class CryptoSyncEngine(
    private val defaultIterations: Int = DEFAULT_ITERATIONS
) {

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
        kdfAlgorithm: String
    ): SecretKeySpec {
        val keyLength = 256
        val spec = PBEKeySpec(passphrase, salt, iterations, keyLength)
        return try {
            val factory = SecretKeyFactory.getInstance(kdfAlgorithm)
            val secretKeyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(secretKeyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encryptData(
        passphrase: String,
        medications: List<Medication>,
        logs: List<MedicationLog>
    ): SyncPayload {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val now = System.currentTimeMillis()

        val key = deriveKey(passphrase.toCharArray(), salt, defaultIterations, KDF_ALGORITHM)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val jsonRoot = JSONObject().apply {
            put("version", CURRENT_VERSION)
            put("created_at", now)

            val medArray = JSONArray()
            medications.forEach { med ->
                medArray.put(JSONObject().apply {
                    put("id", med.id)
                    put("name", med.name)
                    put("dosage", med.dosage)
                    put("form", med.form)
                    put("colorHex", med.colorHex)
                    put("instructions", med.instructions)
                    put("timesOfDay", med.timesOfDay)
                    put("stockQuantity", med.stockQuantity)
                    put("lowStockThreshold", med.lowStockThreshold)
                    put("archived", med.archived)
                    put("createdAt", med.createdAt)
                })
            }
            put("medications", medArray)

            val logArray = JSONArray()
            logs.forEach { log ->
                logArray.put(JSONObject().apply {
                    put("id", log.id)
                    put("medicationId", log.medicationId)
                    put("medicationName", log.medicationName)
                    put("dosage", log.dosage)
                    put("timeSlot", log.timeSlot)
                    put("scheduledDateEpochDay", log.scheduledDateEpochDay)
                    put("scheduledTimeMs", log.scheduledTimeMs)
                    put("takenTimeMs", log.takenTimeMs ?: -1L)
                    put("status", log.status)
                    put("notes", log.notes ?: "")
                })
            }
            put("logs", logArray)
        }

        val plainBytes = jsonRoot.toString().toByteArray(Charsets.UTF_8)
        val encryptedBytes = cipher.doFinal(plainBytes)
        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val digest = computeSha256("$saltBase64:$ivBase64:$encryptedBase64")

        return SyncPayload(
            encryptedData = encryptedBase64,
            iv = ivBase64,
            salt = saltBase64,
            timestamp = now,
            deviceId = "MEDISNAP-FR-" + (1000..9999).random(),
            version = CURRENT_VERSION,
            kdf = KDF_ALGORITHM,
            iterations = defaultIterations,
            sha256Digest = digest,
            medicationCount = medications.size,
            logCount = logs.size
        )
    }

    fun decryptData(
        passphrase: String,
        payload: SyncPayload
    ): Pair<List<Medication>, List<MedicationLog>> {
        if (payload.sha256Digest.isNotBlank()) {
            val expected = computeSha256("${payload.salt}:${payload.iv}:${payload.encryptedData}")
            if (!expected.equals(payload.sha256Digest, ignoreCase = true)) {
                throw SecurityException("Empreinte SHA-256 invalide : le fichier de sauvegarde est altéré.")
            }
        }

        val salt = Base64.decode(payload.salt, Base64.NO_WRAP)
        val iv = Base64.decode(payload.iv, Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(payload.encryptedData, Base64.NO_WRAP)
        val effectiveIterations = when {
            payload.iterations > 0 -> payload.iterations
            payload.version <= 1 -> LEGACY_V1_ITERATIONS
            else -> DEFAULT_ITERATIONS
        }
        val effectiveKdf = payload.kdf.ifBlank { KDF_ALGORITHM }

        val key = deriveKey(passphrase.toCharArray(), salt, effectiveIterations, effectiveKdf)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        val jsonString = String(decryptedBytes, Charsets.UTF_8)
        val jsonRoot = JSONObject(jsonString)

        val meds = mutableListOf<Medication>()
        val medArray = jsonRoot.optJSONArray("medications") ?: JSONArray()
        for (i in 0 until medArray.length()) {
            val obj = medArray.getJSONObject(i)
            meds.add(
                Medication(
                    id = obj.optInt("id", 0),
                    name = obj.optString("name", ""),
                    dosage = obj.optString("dosage", ""),
                    form = obj.optString("form", "Comprimé"),
                    colorHex = obj.optString("colorHex", "#0066FF"),
                    instructions = obj.optString("instructions", ""),
                    timesOfDay = obj.optString("timesOfDay", "08:00"),
                    stockQuantity = obj.optInt("stockQuantity", 30),
                    lowStockThreshold = obj.optInt("lowStockThreshold", 5),
                    archived = obj.optBoolean("archived", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }

        val logs = mutableListOf<MedicationLog>()
        val logArray = jsonRoot.optJSONArray("logs") ?: JSONArray()
        for (i in 0 until logArray.length()) {
            val obj = logArray.getJSONObject(i)
            val takenTimeMs = obj.optLong("takenTimeMs", -1L)
            val notes = obj.optString("notes", "").takeIf { it.isNotBlank() }
            logs.add(
                MedicationLog(
                    id = obj.optLong("id", 0),
                    medicationId = obj.optInt("medicationId", 0),
                    medicationName = obj.optString("medicationName", ""),
                    dosage = obj.optString("dosage", ""),
                    timeSlot = obj.optString("timeSlot", "08:00"),
                    scheduledDateEpochDay = obj.optLong("scheduledDateEpochDay", 0),
                    scheduledTimeMs = obj.optLong("scheduledTimeMs", 0),
                    takenTimeMs = if (takenTimeMs > 0) takenTimeMs else null,
                    status = obj.optString("status", "PENDING"),
                    notes = notes
                )
            )
        }

        return Pair(meds, logs)
    }

    fun serializeEnvelope(payload: SyncPayload): String {
        return JSONObject().apply {
            put("format", ENVELOPE_FORMAT)
            put("standard", "ANSSI-RGS-AES256-GCM")
            put("version", payload.version)
            put("kdf", payload.kdf)
            put("iterations", payload.iterations)
            put("timestamp", payload.timestamp)
            put("deviceId", payload.deviceId)
            put("sha256Digest", payload.sha256Digest)
            put("medicationCount", payload.medicationCount)
            put("logCount", payload.logCount)
            put("salt", payload.salt)
            put("iv", payload.iv)
            put("encryptedData", payload.encryptedData)
        }.toString(2)
    }

    fun parseEnvelope(rawJson: String): SyncPayload {
        val obj = JSONObject(rawJson.trim())
        val encryptedData = obj.optString("encryptedData", "")
        val iv = obj.optString("iv", "")
        val salt = obj.optString("salt", "")
        require(encryptedData.isNotBlank() && iv.isNotBlank() && salt.isNotBlank()) {
            "Format d’archive .medisnap invalide ou incomplet."
        }
        val version = obj.optInt("version", 1)
        val iterations = obj.optInt(
            "iterations",
            if (version <= 1) LEGACY_V1_ITERATIONS else DEFAULT_ITERATIONS
        )
        return SyncPayload(
            encryptedData = encryptedData,
            iv = iv,
            salt = salt,
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            deviceId = obj.optString("deviceId", "ARCHIVE-EXTERNE"),
            version = version,
            kdf = obj.optString("kdf", KDF_ALGORITHM),
            iterations = iterations,
            sha256Digest = obj.optString("sha256Digest", ""),
            medicationCount = obj.optInt("medicationCount", 0),
            logCount = obj.optInt("logCount", 0)
        )
    }

    companion object {
        const val CURRENT_VERSION = 2
        const val LEGACY_V1_ITERATIONS = 10_000
        const val DEFAULT_ITERATIONS = 600_000
        const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ENVELOPE_FORMAT = "medisnap-e2ee-archive"

        fun computeSha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

        fun evaluatePasswordStrength(password: String): PasswordStrength {
            if (password.isEmpty()) {
                return PasswordStrength(
                    score = 0,
                    label = "Non défini",
                    isAnssiRecommended = false,
                    guidance = "Recommandation ANSSI : au moins 12 caractères variés."
                )
            }
            val hasUpper = password.any { it.isUpperCase() }
            val hasLower = password.any { it.isLowerCase() }
            val hasDigit = password.any { it.isDigit() }
            val hasSymbol = password.any { !it.isLetterOrDigit() }
            val varietyCount = listOf(hasUpper, hasLower, hasDigit, hasSymbol).count { it }

            return when {
                password.length < 8 -> PasswordStrength(
                    score = 1,
                    label = "Trop court",
                    isAnssiRecommended = false,
                    guidance = "Minimum 8 caractères requis (12+ recommandés par l’ANSSI)."
                )
                password.length < 12 || varietyCount < 3 -> PasswordStrength(
                    score = 2,
                    label = "Moyen",
                    isAnssiRecommended = false,
                    guidance = "Ajoutez des chiffres, majuscules ou symboles (12+ car. pour niveau ANSSI)."
                )
                password.length >= 12 && varietyCount == 3 -> PasswordStrength(
                    score = 3,
                    label = "Robuste (Conforme ANSSI)",
                    isAnssiRecommended = true,
                    guidance = "Bon niveau de sécurité. Un symbole supplémentaire le rendra optimal."
                )
                else -> PasswordStrength(
                    score = 4,
                    label = "Optimal (Niveau ANSSI / RGS)",
                    isAnssiRecommended = true,
                    guidance = "Excellente entropie contre les attaques par force brute."
                )
            }
        }
    }
}
