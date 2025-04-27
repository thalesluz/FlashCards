package com.example.flashcards.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserLocationDao {
    @Insert
    suspend fun insert(userLocation: UserLocation): Long

    @Query("SELECT * FROM user_location ORDER BY timestamp DESC LIMIT 1")
    fun getLatestLocation(): Flow<UserLocation?>

    @Query("DELETE FROM user_location")
    suspend fun deleteAll()
}