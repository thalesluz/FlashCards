package com.example.flashcards.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.flashcards.data.converter.FlashcardTypeConverter
import java.util.Date

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = Deck::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deckId")]
)
data class Flashcard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deckId: Long,
    val type: FlashcardType = FlashcardType.FRONT_BACK,
    val front: String,
    val back: String,
    val clozeText: String? = null,           // Texto com palavras omitidas para tipo CLOZE
    val clozeAnswer: String? = null,         // Resposta para o tipo CLOZE
    val options: List<String>? = null,       // Opções para múltipla escolha
    val correctOptionIndex: Int? = null,     // Índice da opção correta para múltipla escolha
    val lastReviewed: Date? = null,
    val nextReviewDate: Date? = null,
    val easeFactor: Float = 2.5f,
    val interval: Int = 0,
    val repetitions: Int = 0
) 