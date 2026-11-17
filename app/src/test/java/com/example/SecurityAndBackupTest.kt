package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Medication
import com.example.data.model.MedicationLog
import com.example.data.sync.CryptoSyncEngine
import com.example.security.BiometricAuthManager
import com.example.security.SecurityPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAndBackupTest {

    @Test
    fun `encrypt and decrypt round-trip preserves medications and logs`() {
        val engine = CryptoSyncEngine(defaultIterations = 10_000)
        val meds = listOf(
            Medication(
                id = 1,
                name = "Doliprane",
                dosage = "1000 mg",
                form = "Comprimé",
                instructions = "Avec de l'eau",
                timesOfDay = "08:00,20:00",
                stockQuantity = 16
            )
        )
        val logs = listOf(
            MedicationLog(
                id = 10L,
                medicationId = 1,
                medicationName = "Doliprane",
                dosage = "1000 mg",
                timeSlot = "08:00",
                scheduledDateEpochDay = 20000L,
                scheduledTimeMs = 1700000000000L,
                takenTimeMs = 1700000050000L,
                status = "TAKEN",
                notes = "Pris après repas"
            )
        )

        val payload = engine.encryptData("MonTraitement#2026!", meds, logs)
        val serialized = engine.serializeEnvelope(payload)
        val parsed = engine.parseEnvelope(serialized)
        val (decodedMeds, decodedLogs) = engine.decryptData("MonTraitement#2026!", parsed)

        assertEquals(2, parsed.version)
        assertEquals("PBKDF2WithHmacSHA256", parsed.kdf)
        assertTrue(parsed.sha256Digest.isNotBlank())
        assertEquals(1, decodedMeds.size)
        assertEquals("Doliprane", decodedMeds[0].name)
        assertEquals(1, decodedLogs.size)
        assertEquals("TAKEN", decodedLogs[0].status)
        assertEquals("Pris après repas", decodedLogs[0].notes)
    }

    @Test
    fun `wrong passphrase or tampered digest fails decryption`() {
        val engine = CryptoSyncEngine(defaultIterations = 10_000)
        val meds = listOf(
            Medication(id = 1, name = "Kardegic", dosage = "75 mg", form = "Sachet")
        )
        val payload = engine.encryptData("SecretRobuste#99", meds, emptyList())

        var wrongPasswordFailed = false
        try {
            engine.decryptData("MauvaisMotDePasse", payload)
        } catch (_: Exception) {
            wrongPasswordFailed = true
        }
        assertTrue(wrongPasswordFailed)

        val tampered = payload.copy(encryptedData = payload.encryptedData.reversed())
        var tamperedFailed = false
        try {
            engine.decryptData("SecretRobuste#99", tampered)
        } catch (_: SecurityException) {
            tamperedFailed = true
        }
        assertTrue(tamperedFailed)
    }

    @Test
    fun `anssi password strength evaluator scores weak and strong passphrases`() {
        val weak = CryptoSyncEngine.evaluatePasswordStrength("1234")
        assertFalse(weak.isAnssiRecommended)
        assertEquals(1, weak.score)

        val optimal = CryptoSyncEngine.evaluatePasswordStrength("MonTraitement#2026!")
        assertTrue(optimal.isAnssiRecommended)
        assertEquals(4, optimal.score)
    }

    @Test
    fun `security preferences defaults protect health data immediately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SecurityPreferences(context)
        prefs.clearAll()

        assertTrue(prefs.isBiometricLockEnabled)
        assertTrue(prefs.requireBiometricForSensitiveActions)
        assertFalse(prefs.hideAppPreviewInRecents)
        assertEquals(0, prefs.autoLockTimeoutSeconds)

        prefs.rgpdConsentAcceptedAtMs = 123456789L
        assertEquals(123456789L, prefs.rgpdConsentAcceptedAtMs)

        prefs.clearAll()
        assertEquals(0L, prefs.rgpdConsentAcceptedAtMs)
    }

    @Test
    fun `biometric status returns readable French description`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val description = BiometricAuthManager.getStatusDescription(context)
        assertTrue(description.isNotBlank())
    }
}
