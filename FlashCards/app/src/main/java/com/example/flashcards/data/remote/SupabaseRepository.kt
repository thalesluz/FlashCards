package com.example.flashcards.data.remote

import android.util.Log
import com.example.flashcards.data.Deck
import com.example.flashcards.data.Flashcard
import com.example.flashcards.data.FlashcardType
import com.example.flashcards.data.UserLocation
import com.example.flashcards.data.remote.model.RemoteDeck
import com.example.flashcards.data.remote.model.RemoteFlashcard
import com.example.flashcards.data.remote.model.RemoteUserLocation
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "SupabaseRepository"

class SupabaseRepository {
    private val client = ApiClient.httpClient
    
    // -------- DECKS --------
    
    suspend fun fetchAllDecks(): List<RemoteDeck> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando todos os decks do servidor")
            val result = client.get(SupabaseConfig.SUPABASE_URL + SupabaseConfig.DECKS_ENDPOINT).body<List<RemoteDeck>>()
            Log.d(TAG, "Decks recebidos: ${result.size}")
            result
        } catch (e: ClientRequestException) {
            Log.e(TAG, "Erro do cliente ao buscar decks: ${e.message}", e)
            throw Exception("Erro de requisição: ${e.response.status.value}") 
        } catch (e: ServerResponseException) {
            Log.e(TAG, "Erro do servidor ao buscar decks: ${e.message}", e)
            throw Exception("Erro do servidor: ${e.response.status.value}")
        } catch (e: IOException) {
            Log.e(TAG, "Erro de conexão ao buscar decks: ${e.message}", e)
            throw Exception("Erro de conexão. Verifique sua internet.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro desconhecido ao buscar decks: ${e.message}", e)
            throw Exception("Erro ao sincronizar: ${e.message}")
        }
    }
    
    suspend fun fetchDeck(id: Long): RemoteDeck = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando deck $id")
            // Supabase retorna um array mesmo filtrando por ID único
            val results = client.get(SupabaseConfig.SUPABASE_URL + SupabaseConfig.DECKS_ENDPOINT + "?id=eq.$id&select=*").body<List<RemoteDeck>>()
            results.firstOrNull() ?: throw Exception("Deck com ID $id não encontrado ou resposta inesperada.")
        } catch (e: Exception) {
            // Logar o erro específico de desserialização ou outro
            Log.e(TAG, "Erro ao buscar deck $id: ${e.message}", e)
            // Relançar para ser tratado pela camada superior
            throw e
        }
    }
    
    suspend fun createDeck(deck: Deck): RemoteDeck = withContext(Dispatchers.IO) {
        // Criar um Map para enviar apenas os campos necessários (sem o ID)
        val deckData = mapOf(
            "name" to deck.name,
            "theme" to deck.theme
            // Não incluir "id" nem "created_at", Supabase gerencia
        )
        
        client.post(SupabaseConfig.SUPABASE_URL + SupabaseConfig.DECKS_ENDPOINT) {
            contentType(ContentType.Application.Json)
            header("Prefer", "return=representation") 
            setBody(deckData) // Enviar o Map
        }.body()
    }
    
    suspend fun updateDeck(deck: Deck): RemoteDeck = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Atualizando deck no Supabase: id=${deck.id}, nome=${deck.name}")
            
            // Usar deck.id (Long) diretamente
            val remoteDeck = RemoteDeck(
                id = deck.id, 
                name = deck.name,
                theme = deck.theme,
                created_at = null // Não atualizar created_at
            )
            
            // Usar deck.id (Long) diretamente na query
            client.put(SupabaseConfig.SUPABASE_URL + SupabaseConfig.DECKS_ENDPOINT + "?id=eq.${deck.id}") {
                contentType(ContentType.Application.Json)
                header("Prefer", "return=representation")
                setBody(remoteDeck)
            }
            
            // Retornar o mesmo objeto enviado, já que o Supabase pode não retornar um corpo na resposta
            // ou o corpo pode não conter todos os campos necessários
            return@withContext remoteDeck
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar deck no Supabase: ${e.message}", e)
            throw e
        }
    }
    
    suspend fun deleteDeck(id: Long) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Excluindo deck $id do Supabase")
        client.delete(SupabaseConfig.SUPABASE_URL + SupabaseConfig.DECKS_ENDPOINT + "?id=eq.$id")
            Log.d(TAG, "Deck $id excluído com sucesso")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir deck $id: ${e.message}")
            throw e
        }
    }
    
    // -------- FLASHCARDS --------
    
    suspend fun fetchAllFlashcards(): List<RemoteFlashcard> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando todos os flashcards do servidor")
            val endpoint = "${SupabaseConfig.SUPABASE_URL}${SupabaseConfig.FLASHCARDS_ENDPOINT}"
            Log.d(TAG, "URL de requisição: $endpoint")
            
            // Importante para debugging
            dumpHeaders("Busca de todos os flashcards")
            
            val response = client.get(endpoint)
            Log.d(TAG, "Resposta recebida - Status: ${response.status}")
            
            val result = response.body<List<RemoteFlashcard>>()
            Log.d(TAG, "Flashcards recebidos: ${result.size}")
            
            // Log detalhado para debug
            if (result.isEmpty()) {
                Log.w(TAG, "Nenhum flashcard encontrado no Supabase")
            } else {
                result.take(5).forEachIndexed { index, card ->
                    Log.d(TAG, "Flashcard[$index]: id=${card.id}, deck_id=${card.deck_id}, type=${card.type}, front=${card.front.take(20)}...")
                }
                if (result.size > 5) {
                    Log.d(TAG, "... e mais ${result.size - 5} flashcards")
                }
            }
            
            result
        } catch (e: ClientRequestException) {
            Log.e(TAG, "Erro do cliente ao buscar flashcards: ${e.message}", e)
            Log.e(TAG, "Status: ${e.response.status.value}, Mensagem: ${e.response.status.description}")
            throw Exception("Erro de requisição: ${e.response.status.value}") 
        } catch (e: ServerResponseException) {
            Log.e(TAG, "Erro do servidor ao buscar flashcards: ${e.message}", e)
            Log.e(TAG, "Status: ${e.response.status.value}, Mensagem: ${e.response.status.description}")
            throw Exception("Erro do servidor: ${e.response.status.value}")
        } catch (e: IOException) {
            Log.e(TAG, "Erro de conexão ao buscar flashcards: ${e.message}", e)
            throw Exception("Erro de conexão. Verifique sua internet.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro desconhecido ao buscar flashcards: ${e.message}", e)
            e.printStackTrace()
            throw Exception("Erro ao sincronizar flashcards: ${e.message}")
        }
    }
    
    suspend fun fetchFlashcardsForDeck(deckId: Long): List<RemoteFlashcard> = withContext(Dispatchers.IO) {
        // Remover conversão para String, usar deckId (Long) diretamente
        val endpoint = "${SupabaseConfig.SUPABASE_URL}${SupabaseConfig.FLASHCARDS_ENDPOINT}?deck_id=eq.$deckId"
        Log.d(TAG, "Buscando flashcards para o deck $deckId com URL: $endpoint") // Log da URL completa

        try {
            Log.d(TAG, "Iniciando requisição GET para $endpoint")
            // Importante: fazer dump dos headers para debugging
            dumpHeaders("Busca de flashcards para deck $deckId")
            
            val response = client.get(endpoint)
            Log.d(TAG, "Resposta recebida - Status: ${response.status}")

            // Tentar logar o corpo da resposta antes de desserializar (melhor esforço)
            // Nota: Isso pode falhar se o corpo já foi consumido ou se a resposta não for texto
            try {
                // val responseBodyText = response.bodyAsText() // Ktor < 2.0
                // Log.d(TAG, "Corpo da resposta (texto): ${responseBodyText.take(500)}") // Limita o tamanho do log
                // Com Ktor 2.0+, acessar o corpo após a leitura pode exigir configuração específica ou pode não ser trivial
                // Vamos confiar no status e na desserialização por enquanto.
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível logar o corpo da resposta como texto: ${e.message}")
            }
            
            val result = response.body<List<RemoteFlashcard>>()
            Log.d(TAG, "Desserialização bem-sucedida. Flashcards recebidos para deck $deckId: ${result.size}")
            
            if (result.isEmpty()) {
                Log.w(TAG, "Nenhum flashcard encontrado para o deck $deckId")
                
                // Tenta uma consulta direta para verificar se o problema é com o parâmetro deck_id
                val allFlashcards = fetchAllFlashcards()
                Log.d(TAG, "Consulta alternativa: Total de flashcards no Supabase: ${allFlashcards.size}")
                
                // Filtra manualmente para ver se encontra flashcards com este deck_id
                // Comparar Long com Long
                val filteredFlashcards = allFlashcards.filter { it.deck_id == deckId }
                Log.d(TAG, "Flashcards filtrados manualmente para deck $deckId: ${filteredFlashcards.size}")
                
                if (filteredFlashcards.isNotEmpty()) {
                    Log.w(TAG, "Encontrados ${filteredFlashcards.size} flashcards para o deck $deckId usando filtro manual. Problema na consulta original.")
                    return@withContext filteredFlashcards
                }
            } else {
                result.forEachIndexed { index, card ->
                    Log.d(TAG, "Flashcard[$index]: id=${card.id}, deck_id=${card.deck_id}, type=${card.type}, front=${card.front.take(20)}...")
                }
            }
            
            result
        } catch (e: ClientRequestException) {
            // Tentativa de logar o corpo da resposta em caso de erro do cliente
            val errorBody = try { e.response.body<String>() } catch (ex: Exception) { "N/A (${ex.message})" }
            Log.e(TAG, "Erro do cliente ao buscar flashcards para deck $deckId: ${e.message}", e)
            Log.e(TAG, "Status: ${e.response.status.value}, Mensagem: ${e.response.status.description}")
            Log.e(TAG, "Corpo da Resposta (Erro Cliente): $errorBody") // Log do corpo do erro
            throw Exception("Erro de requisição: ${e.response.status.value}")
        } catch (e: ServerResponseException) {
            // Tentativa de logar o corpo da resposta em caso de erro do servidor
            val errorBody = try { e.response.body<String>() } catch (ex: Exception) { "N/A (${ex.message})" }
            Log.e(TAG, "Erro do servidor ao buscar flashcards para deck $deckId: ${e.message}", e)
            Log.e(TAG, "Status: ${e.response.status.value}, Mensagem: ${e.response.status.description}")
            Log.e(TAG, "Corpo da Resposta (Erro Servidor): $errorBody") // Log do corpo do erro
            throw Exception("Erro do servidor: ${e.response.status.value}")
        } catch (e: IOException) {
            Log.e(TAG, "Erro de conexão ao buscar flashcards para deck $deckId: ${e.message}", e)
            throw Exception("Erro de conexão. Verifique sua internet.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro desconhecido ao buscar flashcards para deck $deckId: ${e.message}", e)
            e.printStackTrace()
            throw Exception("Erro ao buscar flashcards para deck $deckId: ${e.message}")
        }
    }
    
    /**
     * Busca todos os flashcards associados a um deck específico.
     * @param deckId ID do deck no Supabase
     * @return Lista de flashcards remotos
     */
    suspend fun fetchFlashcardsByDeckId(deckId: Long): List<RemoteFlashcard> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando flashcards para o deck $deckId")
            val endpoint = "${SupabaseConfig.SUPABASE_URL}${SupabaseConfig.FLASHCARDS_ENDPOINT}?deck_id=eq.$deckId"
            
            // Enviar requisição com headers adequados
            val response = client.get(endpoint)
            val result = response.body<List<RemoteFlashcard>>()
            
            Log.d(TAG, "Recebidos ${result.size} flashcards para o deck $deckId")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao buscar flashcards para o deck $deckId: ${e.message}", e)
            emptyList<RemoteFlashcard>()
        }
    }
    
    suspend fun createFlashcard(flashcard: Flashcard): RemoteFlashcard = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Criando flashcard no servidor: deck_id=${flashcard.deckId}, tipo=${flashcard.type}")
            
            // Converter para RemoteFlashcard
            val tempRemoteFlashcard = convertToRemoteFlashcard(flashcard)

            // Criar um Map sem o campo 'id' para enviar ao Supabase
            val flashcardData = mutableMapOf<String, Any?>(
                "deck_id" to tempRemoteFlashcard.deck_id,
                "type" to tempRemoteFlashcard.type,
                "front" to tempRemoteFlashcard.front,
                "back" to tempRemoteFlashcard.back
                // Adicionar outros campos conforme necessário, exceto 'id' e 'created_at'
            )
            tempRemoteFlashcard.cloze_text?.let { flashcardData["cloze_text"] = it }
            tempRemoteFlashcard.cloze_answer?.let { flashcardData["cloze_answer"] = it }
            tempRemoteFlashcard.options?.let { flashcardData["options"] = it } // Enviar a lista diretamente
            tempRemoteFlashcard.correct_option_index?.let { flashcardData["correct_option_index"] = it }
            tempRemoteFlashcard.last_reviewed?.let { flashcardData["last_reviewed"] = it }
            tempRemoteFlashcard.next_review_date?.let { flashcardData["next_review_date"] = it }
            tempRemoteFlashcard.ease_factor?.let { flashcardData["ease_factor"] = it }
            tempRemoteFlashcard.interval?.let { flashcardData["interval"] = it }
            tempRemoteFlashcard.repetitions?.let { flashcardData["repetitions"] = it }
            
            Log.d(TAG, "Dados a serem enviados: $flashcardData")
            
            val response = client.post(SupabaseConfig.SUPABASE_URL + SupabaseConfig.FLASHCARDS_ENDPOINT) {
                contentType(ContentType.Application.Json)
                header("Prefer", "return=representation")
                setBody(flashcardData) // Enviar o Map
            }
            
            Log.d(TAG, "Resposta do servidor: ${response.status}")
            
            val result = response.body<RemoteFlashcard>()
            Log.d(TAG, "Flashcard criado com sucesso: ID=${result.id}, deck_id=${result.deck_id}")
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar flashcard no servidor: ${e.message}", e)
            throw e
        }
    }
    
    suspend fun updateFlashcard(flashcard: Flashcard): RemoteFlashcard = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Atualizando flashcard no servidor: id=${flashcard.id}, deck_id=${flashcard.deckId}")
            
            // Usar conversão direta para RemoteFlashcard
            val remoteFlashcard = convertToRemoteFlashcard(flashcard)
            
            Log.d(TAG, "Dados a serem enviados: front=${remoteFlashcard.front.take(20)}..., deck_id=${remoteFlashcard.deck_id}")
            
            client.put(SupabaseConfig.SUPABASE_URL + SupabaseConfig.FLASHCARDS_ENDPOINT + "?id=eq.${flashcard.id}") {
                contentType(ContentType.Application.Json)
                header("Prefer", "return=representation")
                setBody(remoteFlashcard)
            }
            
            // Retornar o mesmo objeto enviado para evitar problemas de desserialização
            return@withContext remoteFlashcard
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar flashcard no servidor: ${e.message}", e)
            throw e
        }
    }
    
    // id já é Long, query está ok
    suspend fun deleteFlashcard(id: Long) = withContext(Dispatchers.IO) {
        client.delete(SupabaseConfig.SUPABASE_URL + SupabaseConfig.FLASHCARDS_ENDPOINT + "?id=eq.$id")
    }
    
    // -------- USER LOCATIONS --------
    
    suspend fun fetchAllLocations(): List<RemoteUserLocation> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Buscando todas as localizações do servidor")
            val result = client.get(SupabaseConfig.SUPABASE_URL + SupabaseConfig.LOCATIONS_ENDPOINT).body<List<RemoteUserLocation>>()
            Log.d(TAG, "Localizações recebidas: ${result.size}")
            result
        } catch (e: ClientRequestException) {
            Log.e(TAG, "Erro do cliente ao buscar localizações: ${e.message}", e)
            throw Exception("Erro de requisição: ${e.response.status.value}") 
        } catch (e: ServerResponseException) {
            Log.e(TAG, "Erro do servidor ao buscar localizações: ${e.message}", e)
            throw Exception("Erro do servidor: ${e.response.status.value}")
        } catch (e: IOException) {
            Log.e(TAG, "Erro de conexão ao buscar localizações: ${e.message}", e)
            throw Exception("Erro de conexão. Verifique sua internet.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro desconhecido ao buscar localizações: ${e.message}", e)
            throw Exception("Erro ao sincronizar: ${e.message}")
        }
    }
    
    suspend fun createLocation(location: UserLocation): RemoteUserLocation = withContext(Dispatchers.IO) {
        // Criar um Map para enviar apenas os campos necessários (sem o ID)
        val locationData = mapOf(
            "name" to location.name,
            "icon_name" to location.iconName,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "timestamp" to location.timestamp.toString()
        )
        
        client.post(SupabaseConfig.SUPABASE_URL + SupabaseConfig.LOCATIONS_ENDPOINT) {
            contentType(ContentType.Application.Json)
            header("Prefer", "return=representation")
            setBody(locationData)
        }.body()
    }
    
    // ID já é Long, query está ok
    suspend fun deleteLocation(id: Long) = withContext(Dispatchers.IO) {
        client.delete(SupabaseConfig.SUPABASE_URL + SupabaseConfig.LOCATIONS_ENDPOINT + "?id=eq.$id")
    }
    
    // -------- CONVERSION HELPERS --------
    
    // Alterado para usar Long para id e deck_id
    private fun convertToRemoteFlashcard(flashcard: Flashcard): RemoteFlashcard {
        // Converter options de String? para List<String>?
        val listConverter = com.example.flashcards.data.converter.ListConverter()
        val optionsList = flashcard.options?.let { listConverter.fromString(it) }
        
        return RemoteFlashcard(
            id = flashcard.id, // Usar Long diretamente
            deck_id = flashcard.deckId, // Usar Long diretamente
            type = flashcard.type.name.lowercase(),
            front = flashcard.front,
            back = flashcard.back,
            cloze_text = flashcard.clozeText,
            cloze_answer = flashcard.clozeAnswer,
            options = optionsList, // Usar a lista convertida
            correct_option_index = flashcard.correctOptionIndex,
            last_reviewed = flashcard.lastReviewed?.toString(), // Manter toString para datas se necessário
            next_review_date = flashcard.nextReviewDate?.toString(), // Manter toString para datas se necessário
            ease_factor = flashcard.easeFactor,
            interval = flashcard.interval,
            repetitions = flashcard.repetitions,
            created_at = null // Não atualizar created_at
        )
    }

    // Função auxiliar para debugging
    private fun dumpHeaders(operation: String) {
        try {
            val headers = mutableMapOf(
                "apikey" to "${SupabaseConfig.SUPABASE_KEY.take(10)}...",
                "Authorization" to "Bearer ${SupabaseConfig.SUPABASE_KEY.take(10)}..."
            )
            Log.d(TAG, "Headers para $operation: $headers")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fazer dump dos headers: ${e.message}")
        }
    }
}