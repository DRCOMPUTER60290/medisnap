package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Medication
import com.example.data.model.MedicationLog
import com.example.data.repository.MedicationRepository
import com.example.data.sync.CryptoSyncEngine
import com.example.data.sync.SyncPayload
import com.example.notification.ReminderScheduler
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MediSnapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MedicationRepository
    private val scheduler = ReminderScheduler(application)
    private val cryptoEngine = CryptoSyncEngine()

    val medications: StateFlow<List<Medication>>
    val todayLogs: StateFlow<List<MedicationLog>>
    val allLogs: StateFlow<List<MedicationLog>>

    private val _syncPayload = MutableStateFlow<SyncPayload?>(null)
    val syncPayload: StateFlow<SyncPayload?> = _syncPayload.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MedicationRepository(db.medicationDao(), db.medicationLogDao())

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

    fun generateE2EEBackup(passphrase: String) {
        viewModelScope.launch {
            val meds = repository.getAllMedicationsForBackup()
            val logs = repository.getAllLogsForBackup()
            if (passphrase.length < 4) {
                _syncMessage.value = "Le mot de passe doit comporter au moins 4 caractères."
                return@launch
            }
            try {
                val payload = cryptoEngine.encryptData(passphrase, meds, logs)
                _syncPayload.value = payload
                _syncMessage.value = "Données chiffrées avec succès (AES-256) ! Clé prête pour la synchronisation cloud."
            } catch (e: Exception) {
                _syncMessage.value = "Erreur de chiffrement : ${e.localizedMessage}"
            }
        }
    }

    fun restoreE2EEBackup(passphrase: String, payload: SyncPayload) {
        viewModelScope.launch {
            try {
                val (meds, logs) = cryptoEngine.decryptData(passphrase, payload)
                repository.restoreData(meds, logs)
                _syncMessage.value = "Restauration chiffrée réussie ! ${meds.size} médicaments synchronisés."
            } catch (e: Exception) {
                _syncMessage.value = "Mot de passe incorrect ou données corrompues."
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }
}
