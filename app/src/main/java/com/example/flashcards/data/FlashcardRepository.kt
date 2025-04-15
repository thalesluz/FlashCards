package com.example.flashcards.data

import kotlinx.coroutines.flow.Flow
import java.util.Date

class FlashcardRepository(private val flashcardDao: FlashcardDao) {
    val allFlashcards: Flow<List<Flashcard>> = flashcardDao.getAllFlashcards()

    fun getFlashcardsForDeck(deckId: Long): Flow<List<Flashcard>> {
        return flashcardDao.getFlashcardsForDeck(deckId)
    }

    fun getAllFlashcards(): Flow<List<Flashcard>> {
        return flashcardDao.getAllFlashcards()
    }

    fun getDueFlashcards(): Flow<List<Flashcard>> {
        return flashcardDao.getDueFlashcards(Date())
    }

    fun getDueFlashcardsForDeck(deckId: Long): Flow<List<Flashcard>> {
        return flashcardDao.getDueFlashcardsForDeck(deckId, Date())
    }

    suspend fun insert(flashcard: Flashcard) {
        flashcardDao.insertFlashcard(flashcard)
    }

    suspend fun update(flashcard: Flashcard) {
        flashcardDao.updateFlashcard(flashcard)
    }

    suspend fun delete(flashcard: Flashcard) {
        flashcardDao.deleteFlashcard(flashcard)
    }

    suspend fun getFlashcardById(id: Long): Flashcard? {
        return flashcardDao.getFlashcardById(id)
    }

    suspend fun deleteAllFlashcardsForDeck(deckId: Long) {
        flashcardDao.deleteAllFlashcardsForDeck(deckId)
    }
} 