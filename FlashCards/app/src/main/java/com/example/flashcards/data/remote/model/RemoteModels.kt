package com.example.flashcards.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteDeck(
    val id: Long,
    val name: String,
    val theme: String? = null,
    @SerialName("created_at") val created_at: String? = null
)

@Serializable
data class RemoteFlashcard(
    val id: Long,
    @SerialName("deck_id") val deck_id: Long,
    val type: String,
    val front: String,
    val back: String,
    @SerialName("cloze_text") val cloze_text: String? = null,
    @SerialName("cloze_answer") val cloze_answer: String? = null,
    val options: List<String>? = null,
    @SerialName("correct_option_index") val correct_option_index: Int? = null,
    @SerialName("last_reviewed") val last_reviewed: String? = null,
    @SerialName("next_review_date") val next_review_date: String? = null,
    @SerialName("ease_factor") val ease_factor: Float? = null,
    val interval: Int? = null,
    val repetitions: Int? = null,
    @SerialName("created_at") val created_at: String? = null
)

@Serializable
data class RemoteUserLocation(
    val id: Long,
    val name: String,
    @SerialName("icon_name") val icon_name: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: String,  // Usando String para timestamptz
    @SerialName("created_at") val created_at: String? = null
) 