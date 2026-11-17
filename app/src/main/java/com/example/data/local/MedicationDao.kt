package com.example.data.local

import androidx.room.*
import com.example.data.model.Medication
import com.example.data.model.MedicationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE archived = 0 ORDER BY createdAt DESC")
    fun getAllActiveMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE archived = 0")
    suspend fun getAllActiveList(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: Int): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long

    @Update
    suspend fun updateMedication(medication: Medication)

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteMedicationById(id: Int)

    @Query("UPDATE medications SET stockQuantity = stockQuantity - 1 WHERE id = :id AND stockQuantity > 0")
    suspend fun decrementStock(id: Int)

    @Query("UPDATE medications SET stockQuantity = stockQuantity + :amount WHERE id = :id")
    suspend fun addStock(id: Int, amount: Int)
}

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_logs WHERE scheduledDateEpochDay = :epochDay ORDER BY scheduledTimeMs ASC")
    fun getLogsForDay(epochDay: Long): Flow<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs ORDER BY scheduledTimeMs DESC LIMIT 200")
    fun getAllLogs(): Flow<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs ORDER BY scheduledTimeMs DESC")
    suspend fun getAllLogsList(): List<MedicationLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: MedicationLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<MedicationLog>)

    @Update
    suspend fun updateLog(log: MedicationLog)

    @Query("UPDATE medication_logs SET status = :status, takenTimeMs = :takenTimeMs WHERE id = :id")
    suspend fun updateLogStatus(id: Long, status: String, takenTimeMs: Long?)

    @Query("SELECT * FROM medication_logs WHERE medicationId = :medId AND scheduledDateEpochDay = :epochDay AND timeSlot = :timeSlot LIMIT 1")
    suspend fun findLog(medId: Int, epochDay: Long, timeSlot: String): MedicationLog?
}
