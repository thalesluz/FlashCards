package com.example.flashcards.ui

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.flashcards.R
import com.example.flashcards.data.remote.model.RemoteDeck

class RemoteDeckAdapter : ListAdapter<RemoteDeck, RemoteDeckAdapter.RemoteDeckViewHolder>(RemoteDeckDiffCallback()) {
    
    private val TAG = "RemoteDeckAdapter"
    private val selectedDecks = mutableSetOf<RemoteDeck>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RemoteDeckViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_remote_deck, parent, false)
        return RemoteDeckViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: RemoteDeckViewHolder, position: Int) {
        val deck = getItem(position)
        Log.d(TAG, "Vinculando deck na posição $position: ID=${deck.id}, Nome='${deck.name}', Tema='${deck.theme}'")
        holder.bind(deck, selectedDecks.contains(deck))
    }
    
    fun getSelectedDecks(): List<RemoteDeck> {
        return selectedDecks.toList()
    }
    
    fun clearSelection() {
        selectedDecks.clear()
        notifyDataSetChanged()
    }
    
    override fun submitList(list: List<RemoteDeck>?) {
        Log.d(TAG, "Submetendo lista com ${list?.size ?: 0} decks")
        if (list != null && list.isNotEmpty()) {
            list.forEachIndexed { index, deck ->
                Log.d(TAG, "Deck #$index na lista: ID=${deck.id}, Nome='${deck.name}', Tema='${deck.theme}'")
            }
        }
        super.submitList(list)
    }
    
    inner class RemoteDeckViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deckNameTextView: TextView = itemView.findViewById(R.id.deckName)
        private val deckThemeTextView: TextView = itemView.findViewById(R.id.deckTheme)
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkbox)
        
        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val deck = getItem(position)
                    toggleDeckSelection(deck)
                }
            }
            
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val deck = getItem(position)
                    if (isChecked) {
                        selectedDecks.add(deck)
                    } else {
                        selectedDecks.remove(deck)
                    }
                }
            }
        }
        
        fun bind(deck: RemoteDeck, isSelected: Boolean) {
            Log.d(TAG, "Configurando TextViews para deck: ID=${deck.id}, Nome='${deck.name}', Tema='${deck.theme}'")
            deckNameTextView.text = deck.name
            deckThemeTextView.text = deck.theme ?: "Sem tema"
            checkBox.isChecked = isSelected
        }
        
        private fun toggleDeckSelection(deck: RemoteDeck) {
            if (selectedDecks.contains(deck)) {
                selectedDecks.remove(deck)
            } else {
                selectedDecks.add(deck)
            }
            notifyItemChanged(adapterPosition)
        }
    }
    
    class RemoteDeckDiffCallback : DiffUtil.ItemCallback<RemoteDeck>() {
        override fun areItemsTheSame(oldItem: RemoteDeck, newItem: RemoteDeck): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: RemoteDeck, newItem: RemoteDeck): Boolean {
            return oldItem.name == newItem.name && 
                   oldItem.theme == newItem.theme
        }
    }
}