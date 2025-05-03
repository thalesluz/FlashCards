package com.example.flashcards.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Deck::class,
        Flashcard::class,
        UserLocation::class,
        WeeklyStats::class
    ],
    version = 2, // Incrementado de 1 para 2 devido às mudanças no esquema
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FlashcardDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun userLocationDao(): UserLocationDao
    abstract fun weeklyStatsDao(): WeeklyStatsDao

    companion object {
        @Volatile
        private var INSTANCE: FlashcardDatabase? = null

        fun getDatabase(context: Context): FlashcardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FlashcardDatabase::class.java,
                    "flashcard_database"
                )
                .fallbackToDestructiveMigration() // Adicionado para lidar com a migração
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}