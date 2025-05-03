package com.example.flashcards.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface WeeklyStatsDao {
    @Query("SELECT * FROM weekly_stats WHERE weekStartDate >= :startOfWeek ORDER BY weekStartDate DESC")
    fun getCurrentWeekStats(startOfWeek: Long = getStartOfWeek()): Flow<WeeklyStats?>

    @Query("SELECT * FROM weekly_stats WHERE weekStartDate >= :startOfWeek ORDER BY weekStartDate DESC LIMIT 4")
    fun getLastFourWeeksStats(startOfWeek: Long = getStartOfWeek()): Flow<List<WeeklyStats>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: WeeklyStats)

    @Update
    suspend fun update(stats: WeeklyStats)

    @Query("SELECT * FROM weekly_stats WHERE weekStartDate >= :startOfWeek")
    suspend fun getCurrentWeekStatsSync(startOfWeek: Long = getStartOfWeek()): WeeklyStats?

    companion object {
        fun getStartOfWeek(): Long {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            return calendar.timeInMillis
        }
    }
} 