package com.example.flashcards.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.flashcards.R
import com.example.flashcards.data.Deck
import com.example.flashcards.util.ColorUtils
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeckAdapter(
    private val onItemClick: (Deck) -> Unit,
    private val onEditClick: (Deck) -> Unit,
    private val getFlashcardCount: (Long) -> Int
) : ListAdapter<Deck, DeckAdapter.DeckViewHolder>(DeckDiffCallback()) {

    // Mapa para armazenar a contagem de flashcards por deck
    private val flashcardCounts = mutableMapOf<Long, Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeckViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deck, parent, false)
        return DeckViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeckViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DeckViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deckHeader: TextView = itemView.findViewById(R.id.deckHeader)
        private val deckThemeText: TextView = itemView.findViewById(R.id.deckThemeText)
        private val cardCountText: TextView = itemView.findViewById(R.id.cardCountText)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val cardView: MaterialCardView = itemView as MaterialCardView

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            editButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEditClick(getItem(position))
                }
            }
        }

        fun bind(deck: Deck) {
            // Gerar cor baseada no nome do deck
            val deckColor = ColorUtils.getColorFromString(deck.name)
            val lighterColor = ColorUtils.getLighterColor(deckColor)
            
            // Aplicar cor ao cabeçalho e à borda do card
            deckHeader.text = itemView.context.getString(R.string.deck_header, deck.name)
            deckHeader.setBackgroundColor(deckColor)
            cardView.strokeColor = deckColor
            
            // Aplicar a cor do deck ao botão de edição
            editButton.setColorFilter(deckColor)
            
            deckThemeText.text = deck.theme
            
            // Exibir a contagem armazenada ou obter uma nova
            val count = flashcardCounts[deck.id] ?: getFlashcardCount(deck.id)
            cardCountText.text = itemView.context.getString(R.string.card_count, count)
        }
    }

    // Método para atualizar a contagem de flashcards para um deck específico
    fun updateFlashcardCount(deckId: Long, count: Int) {
        flashcardCounts[deckId] = count
        val position = currentList.indexOfFirst { it.id == deckId }
        if (position != -1) {
            notifyItemChanged(position)
        }
    }

    private class DeckDiffCallback : DiffUtil.ItemCallback<Deck>() {
        override fun areItemsTheSame(oldItem: Deck, newItem: Deck): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Deck, newItem: Deck): Boolean {
            return oldItem == newItem
        }
    }
} 