package com.example.data

import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val dao: FlashingProfileDao) {
    val allProfiles: Flow<List<FlashingProfile>> = dao.getAllProfiles()

    suspend fun insertProfile(profile: FlashingProfile) {
        dao.insertProfile(profile)
    }

    suspend fun deleteProfile(profile: FlashingProfile) {
        dao.deleteProfile(profile)
    }

    suspend fun getProfileById(id: Int): FlashingProfile? {
        return dao.getProfileById(id)
    }
}
