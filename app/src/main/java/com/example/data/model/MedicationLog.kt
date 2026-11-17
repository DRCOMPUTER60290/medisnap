package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medication_logs")
data class MedicationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Int,
    val medicationName: String,
    val dosage: String,
    val timeSlot: String,
    val scheduledDateEpochDay: Long,
    val scheduledTimeMs: Long,
    val takenTimeMs: Long? = null,
    val status: String = "PENDING",
    val notes: String? = null
)
