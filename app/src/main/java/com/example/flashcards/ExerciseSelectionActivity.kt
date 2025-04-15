package com.example.flashcards

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flashcards.databinding.ActivityExerciseSelectionBinding
import com.example.flashcards.ui.DeckAdapter
import com.example.flashcards.ui.DeckViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ExerciseSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExerciseSelectionBinding
    private lateinit var deckViewModel: DeckViewModel
    private lateinit var adapter: DeckAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar o título da ActionBar
        supportActionBar?.title = "Selecionar Deck para Exercício"
        
        // Habilitar o botão de voltar na ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        deckViewModel = ViewModelProvider(this)[DeckViewModel::class.java]
        setupRecyclerView()
        setupAllDecksButton()
        observeDecks()
    }

    private fun setupRecyclerView() {
        adapter = DeckAdapter(
            onItemClick = { deck -> startExercise(deck.id, deck.name) },
            onEditClick = null,
            getFlashcardCount = { deckId ->
                var count = 0
                lifecycleScope.launch {
                    val actualCount = deckViewModel.getFlashcardCountForDeck(deckId)
                    adapter.updateFlashcardCount(deckId, actualCount)
                }
                count
            }
        )

        binding.recyclerview.apply {
            layoutManager = LinearLayoutManager(this@ExerciseSelectionActivity)
            adapter = this@ExerciseSelectionActivity.adapter
        }
    }

    private fun setupAllDecksButton() {
        binding.allDecksButton.setOnClickListener {
            startExercise(-1, "Todos os Decks")
        }
    }

    private fun observeDecks() {
        lifecycleScope.launch {
            deckViewModel.allDecks.collectLatest { decks ->
                adapter.submitList(decks)
                if (decks.isEmpty()) {
                    binding.emptyView.root.visibility = View.VISIBLE
                    binding.recyclerview.visibility = View.GONE
                } else {
                    binding.emptyView.root.visibility = View.GONE
                    binding.recyclerview.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startExercise(deckId: Long, deckName: String) {
        val intent = Intent(this, ExerciseActivity::class.java)
        intent.putExtra("deckId", deckId)
        intent.putExtra("deckName", deckName)
        startActivity(intent)
    }

    // Sobrescrever o método onSupportNavigateUp para lidar com o clique no botão de voltar
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 