package com.example.data.sync

import android.util.Base64
import com.example.data.model.Medication
import com.example.data.model.MedicationLog
import org.json.JSONArray
import org.json.JSONObject
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
    val deviceId: String
)

class CryptoSyncEngine {

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val iterationCount = 10000
        val keyLength = 256
        val spec = PBEKeySpec(passphrase, salt, iterationCount, keyLength)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKeyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(secretKeyBytes, "AES")
    }

    fun encryptData(
        passphrase: String,
        medications: List<Medication>,
        logs: List<MedicationLog>
    ): SyncPayload {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        val key = deriveKey(passphrase.toCharArray(), salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val jsonRoot = JSONObject().apply {
            put("version", 1)
            put("created_at", System.currentTimeMillis())

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
                })
            }
            put("logs", logArray)
        }

        val plainBytes = jsonRoot.toString().toByteArray(Charsets.UTF_8)
        val encryptedBytes = cipher.doFinal(plainBytes)

        return SyncPayload(
            encryptedData = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
            timestamp = System.currentTimeMillis(),
            deviceId = "MEDISNAP-" + (1000..9999).random()
        )
    }

    fun decryptData(
        passphrase: String,
        payload: SyncPayload
    ): Pair<List<Medication>, List<MedicationLog>> {
        val salt = Base64.decode(payload.salt, Base64.NO_WRAP)
        val iv = Base64.decode(payload.iv, Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(payload.encryptedData, Base64.NO_WRAP)

        val key = deriveKey(passphrase.toCharArray(), salt)
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
                    lowStockThreshold = obj.optInt("lowStockThreshold", 5)
                )
            )
        }

        val logs = mutableListOf<MedicationLog>()
        val logArray = jsonRoot.optJSONArray("logs") ?: JSONArray()
        for (i in 0 until logArray.length()) {
            val obj = logArray.getJSONObject(i)
            val takenTimeMs = obj.optLong("takenTimeMs", -1L)
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
                    status = obj.optString("status", "PENDING")
                )
            )
        }

        return Pair(meds, logs)
    }
}
