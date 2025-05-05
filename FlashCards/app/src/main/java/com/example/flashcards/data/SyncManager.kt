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
        
        try {
            // Verificar se temos o mapeamento de IDs
            if (remoteToLocalIdMap.isEmpty()) {
                // Se não tivermos o mapeamento, precisamos recriar
                syncDecksToRemote() // Isso deve recriar o mapeamento
            }
            
            // Criar mapeamento inverso para busca eficiente: ID local -> ID remoto
            val localToRemoteIdMap = remoteToLocalIdMap.entries.associate { (remoteId, localId) -> localId to remoteId }
            Log.d(TAG, "Mapeamento de IDs locais para remotos: $localToRemoteIdMap")
            
            // Obter todos os flashcards do Supabase para verificar se já existem
            val remoteFlashcards = supabaseRepository.fetchAllFlashcards()
            Log.d(TAG, "Encontrados ${remoteFlashcards.size} flashcards no servidor")
            
            // Mapear flashcards remotos para facilitar busca
            val remoteFlashcardsMap = remoteFlashcards.associate { it.id to it }
            
            // Mapear flashcards remotos por conteúdo para verificar duplicação
            val remoteFlashcardsByContent = remoteFlashcards.groupBy { 
                "${it.deck_id}||${it.front}||${it.back}||${it.type}" 
            }
            
            // Obter todos os flashcards locais
            val localFlashcards = database.flashcardDao().getAllFlashcardsSync()
            Log.d(TAG, "Sincronizando ${localFlashcards.size} flashcards locais para o servidor")
            
            var createdCount = 0
            var updatedCount = 0
            var skippedCount = 0
            
            for (flashcard in localFlashcards) {
                try {
                    // Verificar se o deck deste flashcard precisa ser sincronizado
                    val remoteDeckId = localToRemoteIdMap[flashcard.deckId]
                    if (remoteDeckId == null) {
                        Log.w(TAG, "Ignorando flashcard ${flashcard.id}: o deck local ID=${flashcard.deckId} não está mapeado para nenhum deck remoto")
                        skippedCount++
                        continue
                    }
                    
                    // Gerar chave de conteúdo para verificar duplicação
                    val contentKey = "${remoteDeckId}||${flashcard.front}||${flashcard.back}||${flashcard.type.name.lowercase()}"
                    
                    // Verificar se já existe no remoto por ID
                    val existingRemoteById = if (flashcard.id > 0) remoteFlashcardsMap[flashcard.id] else null
                    
                    // Verificar se já existe no remoto por conteúdo
                    val existingRemoteByContent = remoteFlashcardsByContent[contentKey]?.firstOrNull()
                    
                    when {
                        // Caso 1: Já existe no Supabase com o mesmo ID
                        existingRemoteById != null -> {
                            // Atualizar o flashcard existente, garantindo que use o deck_id correto
                            val remoteFlashcard = flashcard.copy(deckId = remoteDeckId)
                            supabaseRepository.updateFlashcard(remoteFlashcard)
                            Log.d(TAG, "Flashcard atualizado no remoto por ID: ${flashcard.id}")
                            updatedCount++
                        }
                        
                        // Caso 2: Já existe no Supabase com conteúdo similar
                        existingRemoteByContent != null -> {
                            Log.d(TAG, "Flashcard com conteúdo similar encontrado no remoto: ${existingRemoteByContent.id}")
                            // Salvar o mapeamento para uso futuro
                            remoteToLocalIdMap[existingRemoteByContent.id] = flashcard.id
                            
                            // Atualizar se necessário (p.ex., se há novos dados de revisão)
                            val updatedFlashcard = flashcard.copy(id = existingRemoteByContent.id, deckId = remoteDeckId)
                            supabaseRepository.updateFlashcard(updatedFlashcard)
                            Log.d(TAG, "Flashcard atualizado no remoto por conteúdo: ${existingRemoteByContent.id}")
                            updatedCount++
                        }
                        
                        // Caso 3: É um flashcard novo para o Supabase
                        else -> {
                            // Garantir que use o deck_id correto para o Supabase
                            val newFlashcard = flashcard.copy(deckId = remoteDeckId)
                            val result = supabaseRepository.createFlashcard(newFlashcard)
                            Log.d(TAG, "Novo flashcard criado no remoto: ${result.id}")
                            // Salvar o mapeamento para uso futuro
                            remoteToLocalIdMap[result.id] = flashcard.id
                            createdCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao sincronizar flashcard ${flashcard.id} para o remoto", e)
                }
            }
            
            Log.d(TAG, "Sincronização de flashcards para o remoto concluída: $createdCount criados, $updatedCount atualizados, $skippedCount ignorados")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar flashcards para o remoto", e)
            throw e
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

            // Obter todos os flashcards locais para verificações de duplicidade
            val allLocalFlashcards = database.flashcardDao().getAllFlashcardsSync()
            
            // Criar mapeamentos para busca eficiente por ID e conteúdo
            val localFlashcardsByRemoteId = allLocalFlashcards.filter { it.id > 0 }.associateBy { it.id }
            val localFlashcardsByContent = allLocalFlashcards.associateBy { 
                "${it.deckId}||${it.front}||${it.back}||${it.type.name}" 
            }
            
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
                    
                    // Gerar a chave de conteúdo para verificar duplicação baseada no conteúdo
                    val contentKey = "${flashcard.deckId}||${flashcard.front}||${flashcard.back}||${flashcard.type.name}"
                    
                    // Verificar se o flashcard já existe com o mesmo ID
                    val existingFlashcardById = localFlashcardsByRemoteId[flashcard.id]
                    
                    // Verificar se um flashcard com conteúdo similar já existe
                    val existingFlashcardByContent = localFlashcardsByContent[contentKey]
                    
                    when {
                        // Caso 1: Existe um flashcard com o mesmo ID remoto
                        existingFlashcardById != null -> {
                            // Atualizar o flashcard existente se for o mesmo deck
                            if (existingFlashcardById.deckId == localDeckId) {
                                database.flashcardDao().update(flashcard)
                                Log.d(TAG, "Flashcard atualizado por ID: id=${flashcard.id}, deck=${flashcard.deckId}")
                                updatedCount++
                            } else {
                                Log.w(TAG, "Conflito de ID: Flashcard ID=${flashcard.id} existe em dois decks diferentes")
                                // Verificar se conteúdo já existe antes de criar novo
                                if (existingFlashcardByContent == null) {
                                    val newFlashcard = flashcard.copy(id = 0)
                                    val newId = database.flashcardDao().insert(newFlashcard)
                                    Log.d(TAG, "Flashcard inserido com novo ID: ${newId} (conflito de ID)")
                                    insertedCount++
                                } else {
                                    Log.d(TAG, "Flashcard similar já existe, ignorando duplicata: ${existingFlashcardByContent.id}")
                                    skippedCount++
                                }
                            }
                        }
                        
                        // Caso 2: Existe um flashcard com conteúdo similar
                        existingFlashcardByContent != null -> {
                            Log.d(TAG, "Flashcard com conteúdo similar encontrado: ${existingFlashcardByContent.id}")
                            // Atualizar se houver alguma diferença em outros campos (ex: ease_factor, interval)
                            val updatedFlashcard = flashcard.copy(id = existingFlashcardByContent.id)
                            database.flashcardDao().update(updatedFlashcard)
                            Log.d(TAG, "Flashcard atualizado por conteúdo: ${existingFlashcardByContent.id}")
                            updatedCount++
                        }
                        
                        // Caso 3: É um flashcard completamente novo
                        else -> {
                            // Inserir como novo com ID=0 para que o Room atribua um novo ID
                            val newFlashcard = flashcard.copy(id = 0)
                            val newId = database.flashcardDao().insert(newFlashcard)
                            Log.d(TAG, "Novo flashcard inserido: ID=${newId}")
                            insertedCount++
                        }
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
            Log.e(TAG, "Erro ao excluir todos os decks: ${e.message}")
            false
        }
    }

    /**
     * Busca decks disponíveis remotamente que ainda não existem localmente.
     * @return Lista de decks remotos que ainda não foram importados para o dispositivo
     */
    suspend fun fetchAvailableRemoteDecks(): List<RemoteDeck> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando decks disponíveis remotamente")
            
            // Buscar todos os decks do Supabase
            Log.d(TAG, "Conectando ao servidor Supabase...")
            val remoteDecks = try {
                supabaseRepository.fetchAllDecks()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao conectar com o Supabase: ${e.message}", e)
                Log.e(TAG, "Detalhes do erro: ${e.stackTraceToString()}")
                throw Exception("Erro de conexão com o servidor: ${e.message}")
            }
            
            Log.d(TAG, "Conexão com Supabase bem-sucedida")
            
            if (remoteDecks.isEmpty()) {
                Log.w(TAG, "Nenhum deck encontrado no servidor Supabase - o banco de dados remoto está vazio")
                return@withContext emptyList<RemoteDeck>()
            }
            
            Log.d(TAG, "Recebidos ${remoteDecks.size} decks do Supabase")
            
            // Listar todos os decks remotos para debugging
            remoteDecks.forEachIndexed { index, deck ->
                Log.d(TAG, "Deck remoto #$index: ID=${deck.id}, Nome=${deck.name}, Tema=${deck.theme ?: "sem tema"}")
            }
            
            // Obter todos os decks locais para verificar quais já existem
            val localDecks = database.deckDao().getAllDecksSync()
            Log.d(TAG, "Encontrados ${localDecks.size} decks locais")
            
            // Listar decks locais para debugging
            localDecks.forEachIndexed { index, deck ->
                Log.d(TAG, "Deck local #$index: ID=${deck.id}, Nome=${deck.name}, Tema=${deck.theme}")
            }
            
            // Criar mapeamento de decks locais por nome e tema para verificação rápida
            val localDecksByNameAndTheme = localDecks.associateBy { 
                "${it.name.trim().lowercase()}||${it.theme.trim().lowercase()}" 
            }
            
            // Filtra os decks que não existem localmente
            val availableDecks = remoteDecks.filter { remoteDeck ->
                val key = "${remoteDeck.name.trim().lowercase()}||${(remoteDeck.theme ?: "").trim().lowercase()}"
                val exists = localDecksByNameAndTheme.containsKey(key)
                if (exists) {
                    Log.d(TAG, "Deck '${remoteDeck.name}' já existe localmente, ignorando")
                }
                !exists
            }
            
            Log.d(TAG, "Encontrados ${availableDecks.size} decks disponíveis para importação")
            
            // Listar decks disponíveis para importação
            if (availableDecks.isEmpty()) {
                Log.w(TAG, "Todos os decks remotos já existem localmente - nada para importar")
            } else {
                availableDecks.forEachIndexed { index, deck ->
                    Log.d(TAG, "Deck disponível #$index: ID=${deck.id}, Nome=${deck.name}, Tema=${deck.theme ?: "sem tema"}")
                }
            }
            
            return@withContext availableDecks
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar decks remotos disponíveis: ${e.message}", e)
            throw Exception("Erro ao buscar decks: ${e.message}")
        }
    }
    
    /**
     * Importa decks específicos do Supabase para o dispositivo local.
     * @param decksToImport Lista de decks remotos que devem ser importados
     * @return true se ao menos um deck foi importado com sucesso
     */
    suspend fun importRemoteDecks(decksToImport: List<RemoteDeck>): Boolean = withContext(Dispatchers.IO) {
        try {
            if (decksToImport.isEmpty()) {
                return@withContext false
            }
            
            Log.d(TAG, "Importando ${decksToImport.size} decks remotos")
            var importedCount = 0
            
            for (remoteDeck in decksToImport) {
                try {
                    // Criar deck local a partir do deck remoto
                    val localDeck = Deck(
                        id = 0, // Usar ID 0 para que o Room gere um novo ID
                        name = remoteDeck.name,
                        theme = remoteDeck.theme ?: "",
                        createdAt = System.currentTimeMillis()
                    )
                    
                    // Inserir deck no banco de dados local
                    val deckId = database.deckDao().insert(localDeck)
                    Log.d(TAG, "Deck importado: ${remoteDeck.name}, ID local: $deckId")
                    
                    // Mapear o ID remoto para o ID local para importar os flashcards
                    remoteToLocalIdMap[remoteDeck.id] = deckId
                    
                    // Buscar todos os flashcards associados a este deck remoto
                    val remoteFlashcards = supabaseRepository.fetchFlashcardsByDeckId(remoteDeck.id)
                    Log.d(TAG, "Encontrados ${remoteFlashcards.size} flashcards para importar do deck ${remoteDeck.name}")
                    
                    // Importar flashcards deste deck
                    for (remoteFlashcard in remoteFlashcards) {
                        val flashcard = convertRemoteToLocalFlashcard(remoteFlashcard, deckId)
                        val flashcardId = database.flashcardDao().insert(flashcard.copy(id = 0))
                        Log.d(TAG, "Flashcard importado: ID=$flashcardId")
                    }
                    
                    importedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao importar deck ${remoteDeck.id} - ${remoteDeck.name}", e)
                }
            }
            
            Log.d(TAG, "Importação concluída: $importedCount decks importados com sucesso")
            return@withContext importedCount > 0
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao importar decks remotos", e)
            return@withContext false
        }
    }

    /**
     * Sincroniza um único deck específico para o Supabase.
     * @param deck O deck a ser exportado
     * @return O deck remoto criado/atualizado ou null se falhou
     */
    suspend fun syncSingleDeckToRemote(deck: Deck): com.example.flashcards.data.remote.model.RemoteDeck? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Exportando deck específico para Supabase: ${deck.name} (ID: ${deck.id})")
        
        try {
            // Verificar se o deck já existe no servidor
            val remoteDecks = supabaseRepository.fetchAllDecks()
            val normalizedName = deck.name.trim().lowercase()
            val normalizedTheme = deck.theme.trim().lowercase()
            
            // Buscar por correspondência de nome e tema
            val existingDeck = remoteDecks.find { remoteDeck ->
                remoteDeck.name.trim().lowercase() == normalizedName &&
                (remoteDeck.theme ?: "").trim().lowercase() == normalizedTheme
            }
            
            // Tentar também por ID se não encontrou por nome/tema
            val existingDeckById = if (existingDeck == null) remoteDecks.find { it.id == deck.id } else null
            
            return@withContext when {
                // Caso 1: Deck existe por nome e tema
                existingDeck != null -> {
                    Log.d(TAG, "Deck já existe no Supabase, atualizando: ID=${existingDeck.id}")
                    val updatedDeck = deck.copy(id = existingDeck.id)
                    supabaseRepository.updateDeck(updatedDeck)
                    
                    remoteToLocalIdMap[existingDeck.id] = deck.id
                    existingDeck
                }
                
                // Caso 2: Deck existe por ID
                existingDeckById != null -> {
                    Log.d(TAG, "Deck existe com o mesmo ID, atualizando: ID=${existingDeckById.id}")
                    supabaseRepository.updateDeck(deck)
                    
                    remoteToLocalIdMap[deck.id] = deck.id
                    existingDeckById
                }
                
                // Caso 3: Novo deck
                else -> {
                    Log.d(TAG, "Criando novo deck no Supabase: ${deck.name}")
                    val result = supabaseRepository.createDeck(deck)
                    
                    remoteToLocalIdMap[result.id] = deck.id
                    Log.d(TAG, "Novo deck criado no Supabase com ID=${result.id}")
                    result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exportar deck para Supabase: ${e.message}", e)
            null
        }
    }
    
    /**
     * Sincroniza um único flashcard para o Supabase.
     * @param flashcard O flashcard a ser exportado
     * @return O flashcard remoto criado/atualizado ou null se falhou
     */
    suspend fun syncSingleFlashcardToRemote(flashcard: Flashcard): com.example.flashcards.data.remote.model.RemoteFlashcard? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Exportando flashcard para Supabase: ID=${flashcard.id}, Deck=${flashcard.deckId}")
        
        try {
            // Buscar todos os flashcards do Supabase para verificar duplicatas
            val remoteFlashcards = supabaseRepository.fetchAllFlashcards()
            
            // Procurar flashcard com mesmo conteúdo
            val contentKey = "${flashcard.deckId}||${flashcard.front}||${flashcard.back}||${flashcard.type.name}"
            val existingByContent = remoteFlashcards.find { remoteCard ->
                val remoteKey = "${remoteCard.deck_id}||${remoteCard.front}||${remoteCard.back}||${remoteCard.type}"
                contentKey == remoteKey
            }
            
            // Procurar por ID se não encontrou por conteúdo
            val existingById = if (existingByContent == null) remoteFlashcards.find { it.id == flashcard.id } else null
            
            return@withContext when {
                // Caso 1: Flashcard existe com mesmo conteúdo
                existingByContent != null -> {
                    Log.d(TAG, "Flashcard já existe no Supabase, atualizando: ID=${existingByContent.id}")
                    val updatedFlashcard = flashcard.copy(id = existingByContent.id)
                    supabaseRepository.updateFlashcard(updatedFlashcard)
                    existingByContent
                }
                
                // Caso 2: Flashcard existe com mesmo ID
                existingById != null -> {
                    Log.d(TAG, "Flashcard existe com mesmo ID, atualizando: ID=${existingById.id}")
                    supabaseRepository.updateFlashcard(flashcard)
                    existingById
                }
                
                // Caso 3: Novo flashcard
                else -> {
                    Log.d(TAG, "Criando novo flashcard no Supabase")
                    val result = supabaseRepository.createFlashcard(flashcard)
                    Log.d(TAG, "Novo flashcard criado com ID=${result.id}")
                    result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao exportar flashcard para Supabase: ${e.message}", e)
            null
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