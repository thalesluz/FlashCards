package com.example.flashcards.data

import android.content.Context
import android.util.Log
import com.example.flashcards.data.remote.SupabaseRepository
import com.example.flashcards.data.remote.model.RemoteDeck
import com.example.flashcards.data.remote.model.RemoteFlashcard
import com.example.flashcards.data.remote.model.RemoteUserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.util.Date
import kotlin.coroutines.CoroutineContext

class SyncManager(private val context: Context) {
    private val TAG = "SyncManager"
    private val supabaseRepository = SupabaseRepository()
    private val database = FlashcardDatabase.getDatabase(context)
    private val deckRepository = DeckRepository(database.deckDao())
    private val flashcardRepository = FlashcardRepository(database.flashcardDao())
    private val userLocationDao = database.userLocationDao()
    
    // Mantendo um mapeamento global de IDs remotos para IDs locais
    private val remoteToLocalIdMap = mutableMapOf<Long, Long>()
    
    suspend fun syncToRemote(
        syncDecks: Boolean = true,
        syncFlashcards: Boolean = true,
        syncLocations: Boolean = true,
        coroutineContext: CoroutineContext = Dispatchers.IO
    ) = withContext(coroutineContext) {
        try {
            Log.d(TAG, "Iniciando sincronização para o remoto")
            
            if (syncDecks) {
                syncDecksToRemote()
            }
            
            if (syncFlashcards) {
                syncFlashcardsToRemote()
            }
            
            if (syncLocations) {
                syncLocationsToRemote()
            }
            
            Log.d(TAG, "Sincronização para o remoto concluída com sucesso")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro na sincronização para o remoto", e)
            false
        }
    }
    
    suspend fun syncFromRemote(
        syncDecks: Boolean = true,
        syncFlashcards: Boolean = true,
        syncLocations: Boolean = true,
        coroutineContext: CoroutineContext = Dispatchers.IO
    ) = withContext(coroutineContext) {
        try {
            Log.d(TAG, "Iniciando sincronização do remoto")
            
            // Limpar o mapeamento para uma nova sincronização
            remoteToLocalIdMap.clear()
            
            if (syncDecks) {
                syncDecksFromRemote()
            }
            
            if (syncFlashcards) {
                syncFlashcardsFromRemote()
            }
            
            if (syncLocations) {
                syncLocationsFromRemote()
            }
            
            Log.d(TAG, "Sincronização do remoto concluída com sucesso")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro na sincronização do remoto", e)
            false
        }
    }
    
    // -------- MÉTODOS PARA SINCRONIZAÇÃO PARA O REMOTO --------
    
    private suspend fun syncDecksToRemote() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sincronizando decks para o remoto")
        
        try {
            // Primeiro, buscar todos os decks remotos para verificação
            val remoteDecks = supabaseRepository.fetchAllDecks()
            val remoteDecksByName = remoteDecks.groupBy { it.name.trim().lowercase() }
            
            Log.d(TAG, "Encontrados ${remoteDecks.size} decks no servidor")
            
            // Obter todos os decks locais
            val localDecks = database.deckDao().getAllDecksSync()
        
            for (deck in localDecks) {
                try {
                    // Nome normalizado para comparação
                    val normalizedName = deck.name.trim().lowercase()
                    
                    // Verificar se já existe um deck remoto com o mesmo nome
                    val matchingRemoteDecks = remoteDecksByName[normalizedName]
                    val existingRemoteDeck = matchingRemoteDecks?.firstOrNull { 
                        (it.theme ?: "").trim().lowercase() == deck.theme.trim().lowercase()
                    }
                    
                    if (existingRemoteDeck != null) {
                        // Se já existe um deck remoto com mesmo nome e tema
                        Log.d(TAG, "Deck já existe no servidor com ID=${existingRemoteDeck.id}: ${deck.name}")
                        
                        // Atualizar o deck remoto se necessário
                        if (deck.name != existingRemoteDeck.name || deck.theme != (existingRemoteDeck.theme ?: "")) {
                            val remoteDeck = RemoteDeck(
                                id = existingRemoteDeck.id,
                                name = deck.name,
                                theme = deck.theme,
                                created_at = existingRemoteDeck.created_at
                            )
                            supabaseRepository.updateDeck(deck.copy(id = existingRemoteDeck.id))
                            Log.d(TAG, "Deck atualizado no remoto: ${existingRemoteDeck.id} - ${deck.name}")
                        }
                        
                        // Manter o mapeamento do ID remoto para o ID local
                        remoteToLocalIdMap[existingRemoteDeck.id] = deck.id
                    } else {
                        // Verificar se já existe um deck remoto com o mesmo ID
                        val matchingRemoteDeckById = remoteDecks.firstOrNull { it.id == deck.id }
                        
                        if (matchingRemoteDeckById != null) {
                            // Atualizar o deck existente
                            supabaseRepository.updateDeck(deck)
                            Log.d(TAG, "Deck atualizado no remoto por ID: ${deck.id} - ${deck.name}")
                            
                            // Manter o mapeamento
                            remoteToLocalIdMap[deck.id] = deck.id
                        } else {
                            // Criar novo deck no servidor
                            val result = supabaseRepository.createDeck(deck)
                            Log.d(TAG, "Novo deck criado no remoto: ${result.id} - ${deck.name}")
                            
                            // Manter o mapeamento do novo ID remoto para o ID local
                            remoteToLocalIdMap[result.id] = deck.id
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao sincronizar deck ${deck.id} para o remoto", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar decks para o remoto", e)
            throw e
        }
    }
    
    private suspend fun syncFlashcardsToRemote() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sincronizando flashcards para o remoto")
        
        // Obter todos os flashcards locais
        val localFlashcards = database.flashcardDao().getAllFlashcardsSync()
        
        for (flashcard in localFlashcards) {
            try {
                // Verificar se já existe no Supabase por ID - usar Long diretamente
                val remoteId = flashcard.id
                
                try {
                    // Assumindo que temos um método para verificar se existe
                    // Isso é uma simplificação, você precisaria implementar uma lógica para verificar se o flashcard existe
                    supabaseRepository.updateFlashcard(flashcard)
                    Log.d(TAG, "Flashcard atualizado no remoto: ${flashcard.id}")
                } catch (e: Exception) {
                    // Se não existe no remoto, cria um novo
                    supabaseRepository.createFlashcard(flashcard)
                    Log.d(TAG, "Flashcard criado no remoto: ${flashcard.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao sincronizar flashcard ${flashcard.id} para o remoto", e)
            }
        }
    }
    
    private suspend fun syncLocationsToRemote() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sincronizando localizações para o remoto")
        
        // Obter todas as localizações locais
        val localLocations = userLocationDao.getAllLocationsSync()
        
        for (location in localLocations) {
            try {
                // No caso de localizações, sempre criamos novas entradas no remoto
                // Pois cada localização é um registro único no tempo
                supabaseRepository.createLocation(location)
                Log.d(TAG, "Localização criada no remoto: ${location.id} - ${location.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao sincronizar localização ${location.id} para o remoto", e)
            }
        }
    }
    
    // -------- MÉTODOS PARA SINCRONIZAÇÃO DO REMOTO --------
    
    private suspend fun syncDecksFromRemote() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sincronizando decks do remoto")
        
        try {
            // Buscar todos os decks do Supabase
            val remoteDecks = supabaseRepository.fetchAllDecks()
            
            if (remoteDecks.isEmpty()) {
                Log.w(TAG, "Nenhum deck encontrado no servidor Supabase")
                return@withContext
            }
            
            Log.d(TAG, "Recebidos ${remoteDecks.size} decks do Supabase")
            
            // Obter todos os decks locais para verificação de duplicatas
            val localDecks = database.deckDao().getAllDecksSync()
            
            // Criar mapeamento mais eficiente
            val localDecksByNameAndTheme = localDecks.associateBy { 
                "${it.name.trim().lowercase()}||${it.theme.trim().lowercase()}" 
            }
            
            // Processamento de decks remotos
            for (remoteDeck in remoteDecks) {
                try {
                    Log.d(TAG, "Processando deck remoto: id=${remoteDeck.id}, name=${remoteDeck.name}")
                    
                    // Chave para identificação única
                    val key = "${remoteDeck.name.trim().lowercase()}||${(remoteDeck.theme ?: "").trim().lowercase()}"
                    
                    // Verificar se existe um deck local com o mesmo nome e tema
                    val existingDeck = localDecksByNameAndTheme[key]
                    
                    if (existingDeck != null) {
                        // Se existir um deck com mesmo nome e tema, apenas mapeie os IDs
                        Log.d(TAG, "Deck já existe localmente: ${existingDeck.id} - ${existingDeck.name}")
                        remoteToLocalIdMap[remoteDeck.id] = existingDeck.id
                    } else {
                        // Verificar também se existe um deck com o mesmo ID
                        val existingDeckById = database.deckDao().getDeckById(remoteDeck.id)
                        
                        if (existingDeckById != null) {
                            // Atualizar o deck existente
                            val updatedDeck = existingDeckById.copy(
                                name = remoteDeck.name,
                                theme = remoteDeck.theme ?: ""
                            )
                            database.deckDao().update(updatedDeck)
                            Log.d(TAG, "Deck atualizado por ID: ${existingDeckById.id} - ${remoteDeck.name}")
                            remoteToLocalIdMap[remoteDeck.id] = existingDeckById.id
                        } else {
                            // Inserir novo deck apenas se não existir nenhum com mesmo nome/tema ou ID
                            val newDeck = Deck(
                                id = 0, // Usar 0 para permitir que o Room atribua um ID único
                                name = remoteDeck.name,
                                theme = remoteDeck.theme ?: "",
                                createdAt = System.currentTimeMillis()
                            )
                            val insertedId = database.deckDao().insert(newDeck)
                            Log.d(TAG, "Novo deck inserido: ID=${insertedId} - ${remoteDeck.name}")
                            remoteToLocalIdMap[remoteDeck.id] = insertedId
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar deck remoto: ${remoteDeck.id}", e)
                }
            }
            
            // Salvar o mapeamento de IDs para uso posterior
            Log.d(TAG, "Mapeamento de IDs remotos para locais: $remoteToLocalIdMap")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar decks do remoto", e)
            throw e
        }
    }
    
    private suspend fun syncFlashcardsFromRemote() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sincronizando flashcards do remoto")
        
        try {
            // Verificar se temos o mapeamento de IDs
            if (remoteToLocalIdMap.isEmpty()) {
                // Se não tivermos o mapeamento, precisamos recriar
                val allRemoteDecks = supabaseRepository.fetchAllDecks()
                val allLocalDecks = database.deckDao().getAllDecksSync()
                
                // Mapeamento por nome+tema
                val localDecksByNameAndTheme = allLocalDecks.associateBy { 
                    "${it.name.trim().lowercase()}||${it.theme.trim().lowercase()}" 
                }
                
                for (remoteDeck in allRemoteDecks) {
                    val key = "${remoteDeck.name.trim().lowercase()}||${(remoteDeck.theme ?: "").trim().lowercase()}"
                    val localDeck = localDecksByNameAndTheme[key]
                    
                    if (localDeck != null) {
                        remoteToLocalIdMap[remoteDeck.id] = localDeck.id
                        Log.d(TAG, "Mapeamento de deck: remoto ID=${remoteDeck.id} -> local ID=${localDeck.id} (${remoteDeck.name})")
                    }
                }
            }
            
            // Obter todos os flashcards diretamente
            Log.d(TAG, "Buscando todos os flashcards do servidor")
            val allRemoteFlashcards = supabaseRepository.fetchAllFlashcards()
            
            if (allRemoteFlashcards.isEmpty()) {
                Log.w(TAG, "Nenhum flashcard encontrado no servidor")
                return@withContext
            }
            
            Log.d(TAG, "Recebidos ${allRemoteFlashcards.size} flashcards do servidor")
            
            var insertedCount = 0
            var updatedCount = 0
            var skippedCount = 0
            
            // Processar todos os flashcards
            for (remoteFlashcard in allRemoteFlashcards) {
                try {
                    Log.d(TAG, "Processando flashcard: id=${remoteFlashcard.id}, deck_id=${remoteFlashcard.deck_id}")
                    
                    // Obter o ID local do deck mapeado
                    val localDeckId = remoteToLocalIdMap[remoteFlashcard.deck_id]
                    
                    if (localDeckId == null) {
                        Log.w(TAG, "Ignorando flashcard ${remoteFlashcard.id}: deck remoto ID=${remoteFlashcard.deck_id} não mapeado para nenhum deck local")
                        skippedCount++
                        continue
                    }
                    
                    // Modificar o flashcard para usar o ID local do deck
                    val flashcard = convertRemoteToLocalFlashcard(remoteFlashcard, localDeckId)
                    
                    // Verificar se o flashcard já existe 
                    val existingFlashcard = database.flashcardDao().getById(flashcard.id)
                    
                    if (existingFlashcard != null) {
                        // Atualizar o flashcard existente apenas se pertence ao mesmo deck
                        if (existingFlashcard.deckId == localDeckId) {
                            database.flashcardDao().update(flashcard)
                            Log.d(TAG, "Flashcard atualizado: id=${flashcard.id}, deck=${flashcard.deckId}")
                            updatedCount++
                        } else {
                            Log.w(TAG, "Conflito de flashcard: ID=${flashcard.id} existe em dois decks diferentes")
                            // Criar como novo flashcard
                            val newFlashcard = flashcard.copy(id = 0)
                            val newId = database.flashcardDao().insert(newFlashcard)
                            Log.d(TAG, "Flashcard inserido com novo ID: ${newId} (original: ${flashcard.id})")
                            insertedCount++
                        }
                    } else {
                        // Inserir como novo flashcard com id=0 para que o Room atribua um novo ID
                        val newFlashcard = flashcard.copy(id = 0)
                        val newId = database.flashcardDao().insert(newFlashcard)
                        Log.d(TAG, "Novo flashcard inserido: ID=${newId}")
                        insertedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar flashcard ${remoteFlashcard.id}: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "Sincronização de flashcards concluída: $insertedCount inseridos, $updatedCount atualizados, $skippedCount ignorados")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar flashcards do remoto", e)
            throw e
        }
    }
    
    private suspend fun syncLocationsFromRemote() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Sincronizando localizações do remoto")
        
        try {
            // Buscar localizações do servidor
            val remoteLocations = supabaseRepository.fetchAllLocations()
            
            if (remoteLocations.isEmpty()) {
                Log.w(TAG, "Nenhuma localização encontrada no servidor")
                return@withContext
            }
            
            Log.d(TAG, "Recebidas ${remoteLocations.size} localizações do servidor")
            
            // Obter localizações locais para evitar duplicatas
            val localLocations = userLocationDao.getAllLocationsSync()
            val existingLocationIds = localLocations.map { it.id }.toSet()
            
            var newLocationsCount = 0
            
            for (remoteLocation in remoteLocations) {
                try {
                    // Verificar se a localização já existe localmente
                    if (remoteLocation.id in existingLocationIds) {
                        // Localização já existe, pular
                        Log.d(TAG, "Localização já existe localmente: ${remoteLocation.id}")
                        continue
                    }
                    
                    // Converter timestamp de String para Long
                    val timestamp = try {
                        remoteLocation.timestamp.toLongOrNull() ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                    
                    // Converter para modelo local e inserir
                    val userLocation = UserLocation(
                        id = remoteLocation.id,
                        name = remoteLocation.name,
                        iconName = remoteLocation.icon_name,
                        latitude = remoteLocation.latitude,
                        longitude = remoteLocation.longitude,
                        timestamp = timestamp
                    )
                    
                    userLocationDao.insert(userLocation)
                    newLocationsCount++
                    Log.d(TAG, "Nova localização inserida: ${userLocation.id}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar localização ${remoteLocation.id}: ${e.message}", e)
                }
            }
            
            Log.d(TAG, "Sincronização de localizações concluída: $newLocationsCount novas localizações")
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar localizações do remoto", e)
            throw e
        }
    }
    
    // Método helper para converter flashcard remoto para local
    private fun convertRemoteToLocalFlashcard(remoteFlashcard: RemoteFlashcard, localDeckId: Long): Flashcard {
        // Converter tipo de string para enum
        val type = when (remoteFlashcard.type.lowercase()) {
            "front_back" -> FlashcardType.FRONT_BACK
            "cloze" -> FlashcardType.CLOZE
            "text_input" -> FlashcardType.TEXT_INPUT
            "multiple_choice" -> FlashcardType.MULTIPLE_CHOICE
            else -> FlashcardType.BASIC
        }
        
        // Converter options de List<String>? para String? usando Gson
        val optionsString = if (remoteFlashcard.options != null) {
            com.google.gson.Gson().toJson(remoteFlashcard.options)
        } else {
            null
        }
        
        // Converter strings de data para timestamps
        val lastReviewed = remoteFlashcard.last_reviewed?.toLongOrNull()
        val nextReviewDate = remoteFlashcard.next_review_date?.toLongOrNull()
        val createdAt = remoteFlashcard.created_at?.toLongOrNull() ?: System.currentTimeMillis()
        
        return Flashcard(
            id = remoteFlashcard.id,
            deckId = localDeckId,
            type = type,
            front = remoteFlashcard.front,
            back = remoteFlashcard.back,
            clozeText = remoteFlashcard.cloze_text ?: "",
            clozeAnswer = remoteFlashcard.cloze_answer ?: "",
            options = optionsString,
            correctOptionIndex = remoteFlashcard.correct_option_index,
            lastReviewed = lastReviewed,
            nextReviewDate = nextReviewDate,
            easeFactor = remoteFlashcard.ease_factor ?: 2.5f,
            interval = remoteFlashcard.interval ?: 0,
            repetitions = remoteFlashcard.repetitions ?: 0,
            createdAt = createdAt
        )
    }
    
    suspend fun deleteAllDecks(deleteFromRemote: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Excluindo todos os decks localmente" + (if (deleteFromRemote) " e remotamente" else ""))
            
            // Obter todos os decks locais
            val localDecks = database.deckDao().getAllDecksSync()
            
            // Se solicitado, também excluir do remoto
            if (deleteFromRemote) {
                for (deck in localDecks) {
                    try {
                        supabaseRepository.deleteDeck(deck.id)
                        Log.d(TAG, "Deck excluído remotamente: ${deck.id} - ${deck.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao excluir deck ${deck.id} remotamente: ${e.message}", e)
                    }
                }
            }
            
            // Excluir todos os decks localmente (isso também exclui flashcards devido à Foreign Key CASCADE)
            val count = database.deckDao().deleteAll()
            Log.d(TAG, "$count decks excluídos localmente")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir todos os decks: ${e.message}", e)
            false
        }
    }
}

// Extensões para os DAOs para obter dados de forma síncrona
suspend fun DeckDao.getAllDecksSync(): List<Deck> {
    return try {
        // Utilizando kotlinx.coroutines.flow.first() para capturar o primeiro valor emitido pelo flow
        getAllDecks().first()
    } catch (e: Exception) {
        Log.e("SyncManager", "Erro ao obter decks: ${e.message}")
        emptyList()
    }
}

suspend fun FlashcardDao.getAllFlashcardsSync(): List<Flashcard> {
    return try {
        // Utilizando kotlinx.coroutines.flow.first() para capturar o primeiro valor emitido pelo flow
        getAllFlashcardsByCreation().first()
    } catch (e: Exception) {
        Log.e("SyncManager", "Erro ao obter flashcards: ${e.message}")
        emptyList()
    }
}

suspend fun UserLocationDao.getAllLocationsSync(): List<UserLocation> {
    // Como getAllUserLocations() retorna LiveData, precisamos de um tratamento diferente
    val locations = getAllUserLocations().value
    return locations ?: emptyList()
} 