package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "flashing_profiles")
data class FlashingProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileName: String,
    val chipsetType: String, // "MTK", "SPD", "QUALCOMM"
    val authFilePath: String = "",
    val fdl1Path: String = "",
    val fdl2Path: String = "",
    val programmerPath: String = "",
    val scatterPath: String = "",
    val activePartitionsJson: String = "", // comma-separated or json of active partitions (e.g. "boot,system,recovery")
    val timestamp: Long = System.currentTimeMillis()
)
