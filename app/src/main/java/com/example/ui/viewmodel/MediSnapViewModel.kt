package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Medication
import com.example.data.model.MedicationLog
import com.example.data.repository.MedicationRepository
import com.example.data.sync.CryptoSyncEngine
import com.example.data.sync.SovereignWebDavSyncClient
import com.example.data.sync.SyncPayload
import com.example.notification.ReminderScheduler
import com.example.security.KeystoreVaultManager
import com.example.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RestorePreview(
    val sourceLabel: String,
    val payload: SyncPayload,
    val medications: List<Medication>,
    val logs: List<MedicationLog>
)

class MediSnapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MedicationRepository
    private val scheduler = ReminderScheduler(application)
    private val cryptoEngine = CryptoSyncEngine()
    private val webDavClient = SovereignWebDavSyncClient()
    private val securityPrefs = SecurityPreferences(application)

    val medications: StateFlow<List<Medication>>
    val todayLogs: StateFlow<List<MedicationLog>>
    val allLogs: StateFlow<List<MedicationLog>>

    private val _syncPayload = MutableStateFlow<SyncPayload?>(null)
    val syncPayload: StateFlow<SyncPayload?> = _syncPayload.asStateFlow()

    private val _serializedArchive = MutableStateFlow<String?>(null)
    val serializedArchive: StateFlow<String?> = _serializedArchive.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    private val _pendingRestorePreview = MutableStateFlow<RestorePreview?>(null)
    val pendingRestorePreview: StateFlow<RestorePreview?> = _pendingRestorePreview.asStateFlow()

    // Biometric & Privacy Security State
    private val _isBiometricLockEnabled = MutableStateFlow(securityPrefs.isBiometricLockEnabled)
    val isBiometricLockEnabled: StateFlow<Boolean> = _isBiometricLockEnabled.asStateFlow()

    private val _isAppUnlocked = MutableStateFlow(!securityPrefs.isBiometricLockEnabled)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _autoLockTimeoutSeconds = MutableStateFlow(securityPrefs.autoLockTimeoutSeconds)
    val autoLockTimeoutSeconds: StateFlow<Int> = _autoLockTimeoutSeconds.asStateFlow()

    private val _requireBiometricForSensitiveActions =
        MutableStateFlow(securityPrefs.requireBiometricForSensitiveActions)
    val requireBiometricForSensitiveActions: StateFlow<Boolean> =
        _requireBiometricForSensitiveActions.asStateFlow()

    private val _hideAppPreviewInRecents = MutableStateFlow(securityPrefs.hideAppPreviewInRecents)
    val hideAppPreviewInRecents: StateFlow<Boolean> = _hideAppPreviewInRecents.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    // Master Key Vault (Android Keystore)
    private val _isMasterKeyVaultEnabled = MutableStateFlow(securityPrefs.isMasterKeyVaultEnabled)
    val isMasterKeyVaultEnabled: StateFlow<Boolean> = _isMasterKeyVaultEnabled.asStateFlow()

    private val _vaultUnlockedPassphrase = MutableStateFlow<String?>(null)
    val vaultUnlockedPassphrase: StateFlow<String?> = _vaultUnlockedPassphrase.asStateFlow()

    // RGPD & Backup Metadata
    private val _rgpdConsentAcceptedAtMs = MutableStateFlow(securityPrefs.rgpdConsentAcceptedAtMs)
    val rgpdConsentAcceptedAtMs: StateFlow<Long> = _rgpdConsentAcceptedAtMs.asStateFlow()

    private val _lastLocalBackupMs = MutableStateFlow(securityPrefs.lastLocalBackupMs)
    val lastLocalBackupMs: StateFlow<Long> = _lastLocalBackupMs.asStateFlow()

    private val _lastSovereignSyncMs = MutableStateFlow(securityPrefs.lastSovereignSyncMs)
    val lastSovereignSyncMs: StateFlow<Long> = _lastSovereignSyncMs.asStateFlow()

    private val _sovereignWebDavUrl = MutableStateFlow(securityPrefs.sovereignWebDavUrl)
    val sovereignWebDavUrl: StateFlow<String> = _sovereignWebDavUrl.asStateFlow()

    private val _sovereignWebDavUsername = MutableStateFlow(securityPrefs.sovereignWebDavUsername)
    val sovereignWebDavUsername: StateFlow<String> = _sovereignWebDavUsername.asStateFlow()

    private var lastBackgroundTimestampMs: Long = 0L
    private var suppressNextAutoLock: Boolean = false

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MedicationRepository(db.medicationDao(), db.medicationLogDao(), db)

        medications = repository.allActiveMedications
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        todayLogs = repository.getLogsForToday()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allLogs = repository.allLogs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch {
            seedSampleDataIfEmpty()
            repository.ensureTodayLogsGenerated()
        }
    }

    private suspend fun seedSampleDataIfEmpty() {
        val currentMeds = repository.getAllMedicationsForBackup()
        if (currentMeds.isEmpty()) {
            val sample1 = Medication(
                name = "Doliprane",
                dosage = "1000 mg",
                form = "Comprimé",
                colorHex = "#0066FF",
                instructions = "Pendant le repas avec un grand verre d'eau",
                timesOfDay = "08:00,20:00",
                stockQuantity = 24,
                lowStockThreshold = 6
            )
            val sample2 = Medication(
                name = "Amoxicilline",
                dosage = "500 mg",
                form = "Gélule",
                colorHex = "#10B981",
                instructions = "À prendre au début du repas",
                timesOfDay = "08:00,13:00,20:00",
                stockQuantity = 18,
                lowStockThreshold = 5
            )
            val sample3 = Medication(
                name = "Kardegic",
                dosage = "75 mg",
                form = "Sachet",
                colorHex = "#F59E0B",
                instructions = "Diluer dans un demi-verre d'eau à midi",
                timesOfDay = "12:00",
                stockQuantity = 30,
                lowStockThreshold = 7
            )

            repository.addMedication(sample1)
            repository.addMedication(sample2)
            repository.addMedication(sample3)
        }
    }

    // --- Biometric Lock Controls ---

    fun setAuthenticating(active: Boolean) {
        _isAuthenticating.value = active
        if (active) {
            suppressNextAutoLock = true
        }
    }

    fun markExternalActivityLaunched() {
        suppressNextAutoLock = true
    }

    fun onUnlockSucceeded() {
        _isAppUnlocked.value = true
        _isAuthenticating.value = false
        _authErrorMessage.value = null
        lastBackgroundTimestampMs = 0L
        unsealVaultIfAvailable()
    }

    fun onAuthFailedAttempt() {
        _authErrorMessage.value = "Biométrie non reconnue. Veuillez réessayer."
    }

    fun onAuthError(message: String) {
        _isAuthenticating.value = false
        _authErrorMessage.value = message
    }

    fun clearAuthError() {
        _authErrorMessage.value = null
    }

    fun lockApp() {
        if (_isBiometricLockEnabled.value) {
            _isAppUnlocked.value = false
            _authErrorMessage.value = null
            _vaultUnlockedPassphrase.value = null
        }
    }

    fun unlockWhenDeviceUnconfigured() {
        _isAppUnlocked.value = true
        _isAuthenticating.value = false
        _authErrorMessage.value = null
    }

    fun onAppBackgrounded() {
        if (!_isAuthenticating.value && !suppressNextAutoLock) {
            lastBackgroundTimestampMs = System.currentTimeMillis()
        }
    }

    fun onAppForegrounded() {
        if (suppressNextAutoLock) {
            suppressNextAutoLock = false
            lastBackgroundTimestampMs = 0L
            return
        }
        val timeoutSec = _autoLockTimeoutSeconds.value
        if (_isBiometricLockEnabled.value &&
            timeoutSec >= 0 &&
            lastBackgroundTimestampMs > 0L &&
            !_isAuthenticating.value
        ) {
            val elapsedMs = System.currentTimeMillis() - lastBackgroundTimestampMs
            if (elapsedMs >= timeoutSec * 1000L) {
                _isAppUnlocked.value = false
                _vaultUnlockedPassphrase.value = null
                _authErrorMessage.value = null
            }
        }
        lastBackgroundTimestampMs = 0L
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        securityPrefs.isBiometricLockEnabled = enabled
        _isBiometricLockEnabled.value = enabled
        if (!enabled) {
            _isAppUnlocked.value = true
            _authErrorMessage.value = null
        }
    }

    fun setAutoLockTimeoutSeconds(seconds: Int) {
        securityPrefs.autoLockTimeoutSeconds = seconds
        _autoLockTimeoutSeconds.value = seconds
    }

    fun setRequireBiometricForSensitiveActions(required: Boolean) {
        securityPrefs.requireBiometricForSensitiveActions = required
        _requireBiometricForSensitiveActions.value = required
    }

    fun setHideAppPreviewInRecents(hide: Boolean) {
        securityPrefs.hideAppPreviewInRecents = hide
        _hideAppPreviewInRecents.value = hide
    }

    // --- Android Keystore Master Passphrase Vault ---

    fun savePassphraseToVault(passphrase: String) {
        if (passphrase.length < 8) {
            _syncMessage.value = "Saisissez au moins 8 caractères avant d’enregistrer la clé dans le coffre matériel."
            return
        }
        viewModelScope.launch {
            try {
                val sealed = withContext(Dispatchers.Default) {
                    KeystoreVaultManager.sealPassphrase(passphrase)
                }
                securityPrefs.sealedMasterKeyCiphertext = sealed.ciphertextBase64
                securityPrefs.sealedMasterKeyIv = sealed.ivBase64
                securityPrefs.isMasterKeyVaultEnabled = true
                _isMasterKeyVaultEnabled.value = true
                _vaultUnlockedPassphrase.value = passphrase
                _syncMessage.value = "Clé maître scellée dans Android Keystore (AES-256-GCM) et protégée par biométrie."
            } catch (e: Exception) {
                _syncMessage.value = "Impossible d’accéder au coffre matériel : ${e.localizedMessage}"
            }
        }
    }

    fun unsealVaultIfAvailable(): String? {
        if (!securityPrefs.isMasterKeyVaultEnabled) return null
        val cipher = securityPrefs.sealedMasterKeyCiphertext ?: return null
        val iv = securityPrefs.sealedMasterKeyIv ?: return null
        return try {
            val secret = KeystoreVaultManager.unsealPassphrase(cipher, iv)
            _vaultUnlockedPassphrase.value = secret
            secret
        } catch (_: Exception) {
            null
        }
    }

    fun clearPassphraseVault() {
        securityPrefs.clearVaultData()
        KeystoreVaultManager.deleteVaultKey()
        _isMasterKeyVaultEnabled.value = false
        _vaultUnlockedPassphrase.value = null
        _syncMessage.value = "Clé maître supprimée du coffre matériel local."
    }

    // --- Medications & Logs CRUD ---

    fun addMedication(
        name: String,
        dosage: String,
        form: String,
        colorHex: String,
        instructions: String,
        timesOfDay: String,
        stockQuantity: Int
    ) {
        viewModelScope.launch {
            val newMed = Medication(
                name = name,
                dosage = dosage,
                form = form,
                colorHex = colorHex,
                instructions = instructions,
                timesOfDay = timesOfDay,
                stockQuantity = stockQuantity
            )
            repository.addMedication(newMed)
        }
    }

    fun markTaken(log: MedicationLog) {
        viewModelScope.launch {
            repository.markLogTaken(log.id, log.medicationId)
        }
    }

    fun markSkipped(log: MedicationLog) {
        viewModelScope.launch {
            repository.markLogSkipped(log.id)
        }
    }

    fun deleteMedication(id: Int) {
        viewModelScope.launch {
            repository.deleteMedication(id)
        }
    }

    fun addStock(medicationId: Int, amount: Int) {
        viewModelScope.launch {
            repository.addStock(medicationId, amount)
        }
    }

    // --- Hybrid Zero-Knowledge Backup (Local SAF + Sovereign WebDAV) ---

    fun generateE2EEBackup(passphrase: String) {
        if (passphrase.length < 8) {
            _syncMessage.value = "Conformité ANSSI : le mot de passe maître doit comporter au moins 8 caractères (12+ recommandés)."
            return
        }
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val meds = repository.getAllMedicationsForBackup()
                val logs = repository.getAllLogsForBackup()
                val (payload, envelope) = withContext(Dispatchers.Default) {
                    val generated = cryptoEngine.encryptData(passphrase, meds, logs)
                    generated to cryptoEngine.serializeEnvelope(generated)
                }
                _syncPayload.value = payload
                _serializedArchive.value = envelope
                _syncMessage.value =
                    "Archive chiffrée AES-256-GCM (PBKDF2 600k) prête pour export local ou serveur souverain."
            } catch (e: Exception) {
                _syncMessage.value = "Erreur de chiffrement : ${e.localizedMessage}"
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun exportEncryptedArchiveToUri(passphrase: String, uri: Uri) {
        if (passphrase.length < 8) {
            _syncMessage.value = "Veuillez saisir votre mot de passe maître (8 caractères minimum)."
            return
        }
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val meds = repository.getAllMedicationsForBackup()
                val logs = repository.getAllLogsForBackup()
                val (payload, envelope) = withContext(Dispatchers.Default) {
                    val generated = cryptoEngine.encryptData(passphrase, meds, logs)
                    generated to cryptoEngine.serializeEnvelope(generated)
                }
                withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri)?.use { stream ->
                        stream.write(envelope.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: throw IllegalStateException("Impossible d’écrire dans le fichier sélectionné.")
                }
                val now = System.currentTimeMillis()
                securityPrefs.lastLocalBackupMs = now
                _lastLocalBackupMs.value = now
                _syncPayload.value = payload
                _serializedArchive.value = envelope
                _syncMessage.value =
                    "Sauvegarde locale chiffrée (.medisnap) enregistrée (${meds.size} traitements, ${logs.size} prises)."
            } catch (e: Exception) {
                _syncMessage.value = "Échec de l’export local : ${e.localizedMessage}"
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun inspectAndDecryptLocalUri(passphrase: String, uri: Uri) {
        if (passphrase.isBlank()) {
            _syncMessage.value = "Veuillez saisir le mot de passe maître pour déchiffrer l’archive."
            return
        }
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val rawContent = withContext(Dispatchers.IO) {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("Impossible de lire le fichier sélectionné.")
                }
                prepareRestoreFromRaw(passphrase, rawContent, "Fichier local (.medisnap)")
            } catch (e: Exception) {
                _syncMessage.value = "Échec de lecture du fichier : ${e.localizedMessage}"
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun inspectAndDecryptRawArchive(passphrase: String, rawContent: String) {
        if (passphrase.isBlank() || rawContent.isBlank()) {
            _syncMessage.value = "Fournissez le contenu de l’archive .medisnap et le mot de passe maître."
            return
        }
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                prepareRestoreFromRaw(passphrase, rawContent, "Archive collée")
            } finally {
                _backupBusy.value = false
            }
        }
    }

    private suspend fun prepareRestoreFromRaw(
        passphrase: String,
        rawContent: String,
        sourceLabel: String
    ) {
        try {
            val (payload, decoded) = withContext(Dispatchers.Default) {
                val parsed = cryptoEngine.parseEnvelope(rawContent)
                val result = cryptoEngine.decryptData(passphrase, parsed)
                parsed to result
            }
            _pendingRestorePreview.value = RestorePreview(
                sourceLabel = sourceLabel,
                payload = payload,
                medications = decoded.first,
                logs = decoded.second
            )
        } catch (e: SecurityException) {
            _syncMessage.value = e.message ?: "Empreinte de sécurité invalide."
        } catch (_: Exception) {
            _syncMessage.value = "Mot de passe maître incorrect ou archive .medisnap corrompue."
        }
    }

    fun confirmRestore(replaceExisting: Boolean) {
        val preview = _pendingRestorePreview.value ?: return
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                repository.restoreData(preview.medications, preview.logs, replaceExisting)
                _pendingRestorePreview.value = null
                _syncMessage.value =
                    "Restauration réussie (${preview.medications.size} traitements, ${preview.logs.size} prises)."
            } catch (e: Exception) {
                _syncMessage.value = "Erreur lors de la restauration en base : ${e.localizedMessage}"
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun dismissRestorePreview() {
        _pendingRestorePreview.value = null
    }

    fun saveSovereignWebDavConfig(url: String, username: String) {
        securityPrefs.sovereignWebDavUrl = url.trim()
        securityPrefs.sovereignWebDavUsername = username.trim()
        _sovereignWebDavUrl.value = url.trim()
        _sovereignWebDavUsername.value = username.trim()
    }

    fun uploadToSovereignWebDav(
        passphrase: String,
        url: String,
        username: String,
        webDavPassword: String
    ) {
        if (passphrase.length < 8) {
            _syncMessage.value = "Le mot de passe maître doit comporter au moins 8 caractères."
            return
        }
        saveSovereignWebDavConfig(url, username)
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val meds = repository.getAllMedicationsForBackup()
                val logs = repository.getAllLogsForBackup()
                val (payload, envelope) = withContext(Dispatchers.Default) {
                    val generated = cryptoEngine.encryptData(passphrase, meds, logs)
                    generated to cryptoEngine.serializeEnvelope(generated)
                }
                val result = webDavClient.uploadEncryptedArchive(url, username, webDavPassword, envelope)
                result.onSuccess { targetUrl ->
                    val now = System.currentTimeMillis()
                    securityPrefs.lastSovereignSyncMs = now
                    _lastSovereignSyncMs.value = now
                    _syncPayload.value = payload
                    _serializedArchive.value = envelope
                    _syncMessage.value = "Sauvegarde chiffrée envoyée vers le serveur souverain ($targetUrl)."
                }.onFailure { err ->
                    _syncMessage.value = err.message ?: "Échec de l’envoi vers le serveur WebDAV."
                }
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun downloadFromSovereignWebDav(
        passphrase: String,
        url: String,
        username: String,
        webDavPassword: String
    ) {
        if (passphrase.isBlank()) {
            _syncMessage.value = "Saisissez le mot de passe maître pour déchiffrer l’archive distante."
            return
        }
        saveSovereignWebDavConfig(url, username)
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                val result = webDavClient.downloadEncryptedArchive(url, username, webDavPassword)
                result.onSuccess { rawArchive ->
                    prepareRestoreFromRaw(passphrase, rawArchive, "Serveur souverain HTTPS")
                }.onFailure { err ->
                    _syncMessage.value = err.message ?: "Impossible de récupérer l’archive distante."
                }
            } finally {
                _backupBusy.value = false
            }
        }
    }

    // --- RGPD Compliance & Erasure ---

    fun acceptRgpdConsent() {
        val now = System.currentTimeMillis()
        securityPrefs.rgpdConsentAcceptedAtMs = now
        _rgpdConsentAcceptedAtMs.value = now
        _syncMessage.value = "Consentement RGPD (Article 9 — Données de santé) enregistré localement."
    }

    fun eraseAllHealthData() {
        viewModelScope.launch {
            _backupBusy.value = true
            try {
                repository.eraseAllData()
                KeystoreVaultManager.deleteVaultKey()
                securityPrefs.clearAll()
                _syncPayload.value = null
                _serializedArchive.value = null
                _pendingRestorePreview.value = null
                _vaultUnlockedPassphrase.value = null
                _isMasterKeyVaultEnabled.value = false
                _rgpdConsentAcceptedAtMs.value = 0L
                _lastLocalBackupMs.value = 0L
                _lastSovereignSyncMs.value = 0L
                _sovereignWebDavUrl.value = ""
                _sovereignWebDavUsername.value = ""
                _isBiometricLockEnabled.value = securityPrefs.isBiometricLockEnabled
                _autoLockTimeoutSeconds.value = securityPrefs.autoLockTimeoutSeconds
                _requireBiometricForSensitiveActions.value = securityPrefs.requireBiometricForSensitiveActions
                _hideAppPreviewInRecents.value = securityPrefs.hideAppPreviewInRecents
                _syncMessage.value = "Toutes les données de santé locales et clés ont été effacées (Art. 17 RGPD)."
            } catch (e: Exception) {
                _syncMessage.value = "Erreur lors de l’effacement : ${e.localizedMessage}"
            } finally {
                _backupBusy.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
