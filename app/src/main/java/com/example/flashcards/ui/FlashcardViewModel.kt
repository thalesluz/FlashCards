package com.example.flashcards.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flashcards.data.Flashcard
import com.example.flashcards.data.FlashcardDatabase
import com.example.flashcards.data.FlashcardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Date

class FlashcardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: FlashcardRepository
    val allFlashcards: Flow<List<Flashcard>>
    val dueFlashcards: Flow<List<Flashcard>>

    init {
        val flashcardDao = FlashcardDatabase.getDatabase(application).flashcardDao()
        repository = FlashcardRepository(flashcardDao)
        allFlashcards = repository.allFlashcards
        dueFlashcards = repository.getDueFlashcards()
    }

    fun getFlashcardsForDeck(deckId: Long): Flow<List<Flashcard>> {
        return repository.getFlashcardsForDeck(deckId)
    }

    fun getDueFlashcardsForDeck(deckId: Long): Flow<List<Flashcard>> {
        return repository.getDueFlashcardsForDeck(deckId)
    }

    fun insert(flashcard: Flashcard) = viewModelScope.launch {
        repository.insert(flashcard)
    }

    fun update(flashcard: Flashcard) = viewModelScope.launch {
        repository.update(flashcard)
    }

    fun delete(flashcard: Flashcard) = viewModelScope.launch {
        repository.delete(flashcard)
    }

    fun getFlashcardById(id: Long) = viewModelScope.launch {
        repository.getFlashcardById(id)
    }

    fun deleteAllFlashcardsForDeck(deckId: Long) = viewModelScope.launch {
        repository.deleteAllFlashcardsForDeck(deckId)
    }

    fun calculateNextReview(flashcard: Flashcard, quality: Int): Flashcard {
        val now = Date()
        val newEaseFactor = calculateNewEaseFactor(flashcard.easeFactor, quality)
        val newInterval = calculateNewInterval(flashcard.interval, newEaseFactor, quality)
        val nextReview = Date(now.time + (newInterval * 24 * 60 * 60 * 1000L))

        return flashcard.copy(
            lastReviewed = now,
            nextReviewDate = nextReview,
            easeFactor = newEaseFactor,
            interval = newInterval,
            repetitions = flashcard.repetitions + 1
        )
    }

    private fun calculateNewEaseFactor(oldEaseFactor: Float, quality: Int): Float {
        val newEaseFactor = oldEaseFactor + (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
        return maxOf(1.3f, newEaseFactor)
    }

    private fun calculateNewInterval(oldInterval: Int, easeFactor: Float, quality: Int): Int {
        return when {
            quality < 3 -> 1
            oldInterval == 0 -> 1
            oldInterval == 1 -> 6
            else -> (oldInterval * easeFactor).toInt()
        }
    }

    fun getAllFlashcards(): Flow<List<Flashcard>> {
        return repository.getAllFlashcards()
    }
} 