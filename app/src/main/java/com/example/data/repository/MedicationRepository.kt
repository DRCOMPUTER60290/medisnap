package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.MedicationDao
import com.example.data.local.MedicationLogDao
import com.example.data.model.Medication
import com.example.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val medicationLogDao: MedicationLogDao,
    private val database: AppDatabase? = null
) {
    val allActiveMedications: Flow<List<Medication>> = medicationDao.getAllActiveMedications()
    val allLogs: Flow<List<MedicationLog>> = medicationLogDao.getAllLogs()

    fun getLogsForToday(): Flow<List<MedicationLog>> {
        val todayEpochDay = getTodayEpochDay()
        return medicationLogDao.getLogsForDay(todayEpochDay)
    }

    suspend fun addMedication(medication: Medication): Long {
        val id = medicationDao.insertMedication(medication)
        generateLogsForMedication(medication.copy(id = id.toInt()))
        return id
    }

    suspend fun updateMedication(medication: Medication) {
        medicationDao.updateMedication(medication)
    }

    suspend fun deleteMedication(id: Int) {
        medicationDao.deleteMedicationById(id)
    }

    suspend fun markLogTaken(logId: Long, medicationId: Int) {
        medicationLogDao.updateLogStatus(logId, "TAKEN", System.currentTimeMillis())
        medicationDao.decrementStock(medicationId)
    }

    suspend fun markLogSkipped(logId: Long) {
        medicationLogDao.updateLogStatus(logId, "SKIPPED", null)
    }

    suspend fun addStock(medicationId: Int, amount: Int) {
        medicationDao.addStock(medicationId, amount)
    }

    suspend fun ensureTodayLogsGenerated() {
        val meds = medicationDao.getAllActiveList()
        val todayEpochDay = getTodayEpochDay()
        val calendar = Calendar.getInstance()

        meds.forEach { med ->
            val times = med.timesOfDay.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            times.forEach { timeSlot ->
                val existing = medicationLogDao.findLog(med.id, todayEpochDay, timeSlot)
                if (existing == null) {
                    val parts = timeSlot.split(":")
                    if (parts.size == 2) {
                        calendar.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 8)
                        calendar.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)

                        medicationLogDao.insertLog(
                            MedicationLog(
                                medicationId = med.id,
                                medicationName = med.name,
                                dosage = med.dosage,
                                timeSlot = timeSlot,
                                scheduledDateEpochDay = todayEpochDay,
                                scheduledTimeMs = calendar.timeInMillis,
                                status = "PENDING"
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun generateLogsForMedication(medication: Medication) {
        val todayEpochDay = getTodayEpochDay()
        val calendar = Calendar.getInstance()
        val times = medication.timesOfDay.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        times.forEach { timeSlot ->
            val parts = timeSlot.split(":")
            if (parts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 8)
                calendar.set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                medicationLogDao.insertLog(
                    MedicationLog(
                        medicationId = medication.id,
                        medicationName = medication.name,
                        dosage = medication.dosage,
                        timeSlot = timeSlot,
                        scheduledDateEpochDay = todayEpochDay,
                        scheduledTimeMs = calendar.timeInMillis,
                        status = "PENDING"
                    )
                )
            }
        }
    }

    suspend fun getAllMedicationsForBackup(): List<Medication> = medicationDao.getAllActiveList()
    suspend fun getAllLogsForBackup(): List<MedicationLog> = medicationLogDao.getAllLogsList()

    suspend fun restoreData(
        meds: List<Medication>,
        logs: List<MedicationLog>,
        replaceExisting: Boolean = false
    ) {
        val executeRestore: suspend () -> Unit = {
            if (replaceExisting) {
                medicationLogDao.deleteAllLogs()
                medicationDao.deleteAllMedications()
                meds.forEach { medicationDao.insertMedication(it) }
                medicationLogDao.insertLogs(logs)
            } else {
                val idMapping = mutableMapOf<Int, Int>()
                meds.forEach { med ->
                    val newId = medicationDao.insertMedication(med.copy(id = 0)).toInt()
                    idMapping[med.id] = newId
                }
                val remappedLogs = logs.map { log ->
                    val mappedMedId = idMapping[log.medicationId] ?: log.medicationId
                    log.copy(id = 0, medicationId = mappedMedId)
                }
                medicationLogDao.insertLogs(remappedLogs)
            }
        }

        if (database != null) {
            database.withTransaction { executeRestore() }
        } else {
            executeRestore()
        }
    }

    suspend fun eraseAllData() {
        val executeErase: suspend () -> Unit = {
            medicationLogDao.deleteAllLogs()
            medicationDao.deleteAllMedications()
        }
        if (database != null) {
            database.withTransaction { executeErase() }
        } else {
            executeErase()
        }
    }

    private fun getTodayEpochDay(): Long {
        return System.currentTimeMillis() / (1000 * 60 * 60 * 24)
    }
}
