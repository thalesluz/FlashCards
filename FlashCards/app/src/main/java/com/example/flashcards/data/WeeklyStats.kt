package com.example.flashcards.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "weekly_stats")
data class WeeklyStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weekStartDate: Long = System.currentTimeMillis(),
    val cardsReviewed: Int = 0,
    val cardsCorrect: Int = 0,
    val cardsIncorrect: Int = 0,
    val frontBackCount: Int = 0,
    val clozeCount: Int = 0,
    val textInputCount: Int = 0,
    val multipleChoiceCount: Int = 0,
    val basicCount: Int = 0
) {
    companion object {
        fun getCurrentWeekStart(): Long {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            return calendar.timeInMillis
        }
    }
} 