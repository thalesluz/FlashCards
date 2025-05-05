package com.example.flashcards.data

import kotlinx.coroutines.flow.Flow

class DeckRepository(private val deckDao: DeckDao) {
    val allDecks: Flow<List<Deck>> = deckDao.getAllDecks()

    suspend fun getDeckById(id: Long): Deck? {
        return deckDao.getDeckById(id)
    }

    suspend fun insert(deck: Deck): Long {
        return deckDao.insert(deck)
    }

    suspend fun update(deck: Deck) {
        deckDao.update(deck)
    }

    suspend fun delete(deck: Deck) {
        deckDao.delete(deck)
    }

    suspend fun getFlashcardCountForDeck(deckId: Long): Int {
        return deckDao.getFlashcardCountForDeck(deckId)
    }

    suspend fun deleteAll(): Int {
        return deckDao.deleteAll()
    }
    
    /**
     * Força uma atualização dos dados no repositório.
     * Como estamos usando Flow, na maioria dos casos isso não é necessário,
     * mas pode ser útil em situações específicas como após importação de dados.
     */
    suspend fun refreshDecks() {
        // O Room com Flow já atualiza automaticamente os dados quando há mudanças no banco de dados.
        // Este método existe principalmente para compatibilidade com a chamada no ViewModel.
        // Se necessário, operações adicionais de atualização podem ser adicionadas aqui.
    }
}