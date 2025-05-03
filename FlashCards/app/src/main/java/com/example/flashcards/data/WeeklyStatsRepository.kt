package com.example.flashcards.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WeeklyStatsRepository(private val weeklyStatsDao: WeeklyStatsDao) {
    fun getCurrentWeekStats(): Flow<WeeklyStats?> = weeklyStatsDao.getCurrentWeekStats()

    fun getLastFourWeeksStats(): Flow<List<WeeklyStats>> = weeklyStatsDao.getLastFourWeeksStats()

    suspend fun updateStats(flashcardType: FlashcardType, isCorrect: Boolean) {
        val currentWeekStart = WeeklyStatsDao.getStartOfWeek()
        var stats = weeklyStatsDao.getCurrentWeekStatsSync(currentWeekStart)

        if (stats == null) {
            stats = WeeklyStats(weekStartDate = currentWeekStart)
            weeklyStatsDao.insert(stats)
        }

        stats = stats.copy(
            cardsReviewed = stats.cardsReviewed + 1,
            cardsCorrect = stats.cardsCorrect + if (isCorrect) 1 else 0,
            cardsIncorrect = stats.cardsIncorrect + if (!isCorrect) 1 else 0,
            frontBackCount = stats.frontBackCount + if (flashcardType == FlashcardType.FRONT_BACK) 1 else 0,
            clozeCount = stats.clozeCount + if (flashcardType == FlashcardType.CLOZE) 1 else 0,
            textInputCount = stats.textInputCount + if (flashcardType == FlashcardType.TEXT_INPUT) 1 else 0,
            multipleChoiceCount = stats.multipleChoiceCount + if (flashcardType == FlashcardType.MULTIPLE_CHOICE) 1 else 0,
            basicCount = stats.basicCount + if (flashcardType == FlashcardType.BASIC) 1 else 0
        )

        weeklyStatsDao.update(stats)
    }
} 