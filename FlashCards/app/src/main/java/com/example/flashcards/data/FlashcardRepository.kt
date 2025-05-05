package com.example.flashcards.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FlashcardRepository(private val dao: FlashcardDao) {
    // Para repetição espaçada
    val allFlashcardsByReview: Flow<List<Flashcard>> = dao.getAllFlashcardsByReview()

    // Para visualização normal
    val allFlashcardsByCreation: Flow<List<Flashcard>> = dao.getAllFlashcardsByCreation()

    fun getFlashcardsForDeckByReview(deckId: Long): Flow<List<Flashcard>> {
        return dao.getFlashcardsForDeckByReview(deckId)
    }

    fun getFlashcardsForDeckByCreation(deckId: Long): Flow<List<Flashcard>> {
        return dao.getFlashcardsForDeckByCreation(deckId)
    }

    fun getDueFlashcards(): Flow<List<Flashcard>> {
        return dao.getDueFlashcards(System.currentTimeMillis())
    }

    fun getDueFlashcardsForDeck(deckId: Long): Flow<List<Flashcard>> {
        return dao.getDueFlashcardsForDeck(deckId, System.currentTimeMillis())
    }

    suspend fun insert(flashcard: Flashcard) {
        dao.insert(flashcard)
    }

    suspend fun update(flashcard: Flashcard) {
        dao.update(flashcard)
    }

    suspend fun delete(flashcard: Flashcard) {
        dao.delete(flashcard)
    }

    suspend fun getById(id: Long): Flashcard? {
        return dao.getById(id)
    }

    suspend fun deleteAllForDeck(deckId: Long) {
        dao.deleteAllForDeck(deckId)
    }

    /**
     * Retorna todos os flashcards de um deck específico de forma síncrona
     */
    suspend fun getFlashcardsForDeckSync(deckId: Long): List<Flashcard> {
        return try {
            dao.getFlashcardsForDeckByCreation(deckId).first()
        } catch (e: Exception) {
            emptyList()
        }
    }
}