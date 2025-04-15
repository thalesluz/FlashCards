package com.example.flashcards.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY nextReviewDate ASC")
    fun getAllFlashcards(): Flow<List<Flashcard>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY nextReviewDate ASC")
    fun getFlashcardsForDeck(deckId: Long): Flow<List<Flashcard>>

    @Query("SELECT * FROM flashcards WHERE nextReviewDate <= :date OR nextReviewDate IS NULL")
    fun getDueFlashcards(date: Date): Flow<List<Flashcard>>

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId AND (nextReviewDate <= :date OR nextReviewDate IS NULL)")
    fun getDueFlashcardsForDeck(deckId: Long, date: Date): Flow<List<Flashcard>>

    @Insert
    suspend fun insertFlashcard(flashcard: Flashcard)

    @Update
    suspend fun updateFlashcard(flashcard: Flashcard)

    @Delete
    suspend fun deleteFlashcard(flashcard: Flashcard)

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getFlashcardById(id: Long): Flashcard?

    @Query("DELETE FROM flashcards WHERE deckId = :deckId")
    suspend fun deleteAllFlashcardsForDeck(deckId: Long)

    @Query("SELECT * FROM flashcards WHERE deckId = :deckId ORDER BY createdAt DESC")
    fun getFlashcardsForDeck(deckId: Long): Flow<List<Flashcard>>

    @Query("SELECT * FROM flashcards ORDER BY createdAt DESC")
    fun getAllFlashcards(): Flow<List<Flashcard>>
} 