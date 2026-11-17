package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val dosage: String,
    val form: String,
    val colorHex: String = "#0066FF",
    val instructions: String = "",
    val timesOfDay: String = "08:00",
    val stockQuantity: Int = 30,
    val lowStockThreshold: Int = 5,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
