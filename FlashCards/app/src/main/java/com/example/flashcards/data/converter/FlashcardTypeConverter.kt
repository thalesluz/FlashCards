package com.example.flashcards.data.converter

import androidx.room.TypeConverter
import com.example.flashcards.data.FlashcardType

class FlashcardTypeConverter {
    @TypeConverter
    fun fromFlashcardType(value: FlashcardType): String {
        return value.name
    }

    @TypeConverter
    fun toFlashcardType(value: String): FlashcardType {
        return FlashcardType.valueOf(value)
    }
} 