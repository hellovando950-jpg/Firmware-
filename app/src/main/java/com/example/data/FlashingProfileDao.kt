package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashingProfileDao {
    @Query("SELECT * FROM flashing_profiles ORDER BY timestamp DESC")
    fun getAllProfiles(): Flow<List<FlashingProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: FlashingProfile)

    @Delete
    suspend fun deleteProfile(profile: FlashingProfile)

    @Query("SELECT * FROM flashing_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: Int): FlashingProfile?
}
