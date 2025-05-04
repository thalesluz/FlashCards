package com.example.flashcards.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    // Flashcards ordenados por data de revisão (para sistema de repetição espaçada)
    @Query("SELECT * FROM flashcards ORDER BY nextReviewDate ASC")
    fun getAllFlashcardsByReview(): Flow<List<Flashcard>>

    // Flashcards ordenados por criação (para visualização padrão)
    @Query("SELECT * FROM flashcards ORDER BY createdAt DESC")
    fun getAllFlashcardsByCreation(): Flow<List<Flashcard>>

    // Flashcards de um deck específico ordenados por revisão
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY nextReviewDate ASC")
    fun getFlashcardsForDeckByReview(deckId: Long): Flow<List<Flashcard>>

    // Flashcards de um deck específico ordenados por criação
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun getFlashcardsForDeckByCreation(deckId: Long): Flow<List<Flashcard>>

    // Flashcards pendentes (para repetição espaçada)
    @Query("SELECT * FROM flashcards WHERE nextReviewDate <= :date OR nextReviewDate IS NULL")
    fun getDueFlashcards(date: Long): Flow<List<Flashcard>>

    // Flashcards pendentes de um deck específico
    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND (nextReviewDate <= :date OR nextReviewDate IS NULL)")
    fun getDueFlashcardsForDeck(deckId: Long, date: Long): Flow<List<Flashcard>>

    // Operações CRUD
    
    // Inserir flashcard e retornar o ID gerado pelo Room
    @Insert
    suspend fun insert(flashcard: Flashcard): Long

    @Update
    suspend fun update(flashcard: Flashcard)

    @Delete
    suspend fun delete(flashcard: Flashcard)

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getById(id: Long): Flashcard?

    @Query("DELETE FROM flashcards WHERE deckId = :deckId")
    suspend fun deleteAllForDeck(deckId: Long)
}