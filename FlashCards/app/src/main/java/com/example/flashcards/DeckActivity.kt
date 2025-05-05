package com.example.flashcards

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flashcards.data.Deck
import com.example.flashcards.data.SyncManager
import com.example.flashcards.databinding.ActivityDeckBinding
import com.example.flashcards.ui.DeckAdapter
import com.example.flashcards.ui.DeckViewModel
import com.example.flashcards.ui.FlashcardViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeckActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDeckBinding
    private lateinit var deckViewModel: DeckViewModel
    private lateinit var flashcardViewModel: FlashcardViewModel
    private lateinit var adapter: DeckAdapter
    private lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar a Toolbar como uma barra de ação simplificada
        binding.toolbar.title = "Decks"
        setSupportActionBar(binding.toolbar)

        deckViewModel = ViewModelProvider(this)[DeckViewModel::class.java]
        flashcardViewModel = ViewModelProvider(this)[FlashcardViewModel::class.java]
        syncManager = SyncManager(this)

        setupRecyclerView()
        setupFab()
        setupBottomNavigation()
        observeDecks()
    }

    // Criar o menu da toolbar
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    // Tratar cliques no menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sync -> {
                lifecycleScope.launch {
                    Toast.makeText(this@DeckActivity, "Sincronizando...", Toast.LENGTH_SHORT).show()
                    syncManager.syncFromRemote()
                    syncManager.syncToRemote()
                    Toast.makeText(this@DeckActivity, "Sincronização concluída", Toast.LENGTH_SHORT).show()
                }
                true
            }
            R.id.action_delete_all_decks -> {
                showDeleteAllDecksDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    // Diálogo para confirmar exclusão de todos os decks
    private fun showDeleteAllDecksDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_all_decks, null)
        val deleteRemoteCheckBox = dialogView.findViewById<CheckBox>(R.id.deleteRemoteCheckBox)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_all_decks))
            .setView(dialogView)
            .setMessage(getString(R.string.delete_all_decks_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    val deleteRemote = deleteRemoteCheckBox.isChecked
                    val result = syncManager.deleteAllDecks(deleteRemote)
                    if (result) {
                        Toast.makeText(
                            this@DeckActivity,
                            getString(R.string.delete_all_decks_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@DeckActivity,
                            "Erro ao excluir decks",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun setupBottomNavigation() {
        // Definindo explicitamente que o item selecionado é o de Decks
        binding.bottomNavigation.selectedItemId = R.id.navigation_decks
        
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish() // Finaliza a atividade atual para evitar acúmulo na pilha
                    true
                }
                R.id.navigation_decks -> true
                R.id.navigation_exercise -> {
                    startActivity(Intent(this, ExerciseSelectionActivity::class.java))
                    finish() // Finaliza a atividade atual para evitar acúmulo na pilha
                    true
                }
                R.id.navigation_environments -> {
                    startActivity(Intent(this, EnvironmentsActivity::class.java))
                    finish() // Finaliza a atividade atual para evitar acúmulo na pilha
                    true
                }
                else -> false
            }
        }
    }

    private fun startExercise() {
        val intent = Intent(this, ExerciseSelectionActivity::class.java)
        startActivity(intent)
        finish() // Finaliza a atividade atual para evitar acúmulo na pilha
    }

    private fun setupRecyclerView() {
        adapter = DeckAdapter(
            onItemClick = { deck -> openFlashcardActivity(deck) },
            onEditClick = { deck -> showAddDeckDialog(deck) },
            getFlashcardCount = { deckId ->
                var count = 0
                lifecycleScope.launch {
                    count = deckViewModel.getFlashcardCountForDeck(deckId)
                    adapter.updateFlashcardCount(deckId, count)
                }
                count
            }
        )

        binding.recyclerview.apply {
            layoutManager = LinearLayoutManager(this@DeckActivity)
            adapter = this@DeckActivity.adapter
        }
    }

    private fun setupFab() {
        binding.fab.setOnClickListener {
            showAddDeckDialog()
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

    private fun showAddDeckDialog(deck: Deck? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_deck, null)
        val nameEditText = dialogView.findViewById<EditText>(R.id.deckNameEditText)
        val themeEditText = dialogView.findViewById<EditText>(R.id.deckThemeEditText)

        deck?.let {
            nameEditText.setText(it.name)
            themeEditText.setText(it.theme)
        }

        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(getString(if (deck == null) R.string.add_deck else R.string.edit_deck))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = nameEditText.text.toString()
                val theme = themeEditText.text.toString()

                if (name.isBlank() || theme.isBlank()) {
                    Toast.makeText(this, "Nome e tema são obrigatórios", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (deck == null) {
                    addDeck(name, theme)
                } else {
                    updateDeck(deck, name, theme)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)

        if (deck != null) {
            // Se estamos editando um deck existente, adicionar botão de opções
            dialogBuilder.setNeutralButton(getString(R.string.more_options)) { _, _ ->
                showDeckOptionsDialog(deck)
            }
        }

        dialogBuilder.show()
    }
    
    // Função para mostrar as opções adicionais para um deck
    private fun showDeckOptionsDialog(deck: Deck) {
        val options = arrayOf(
            getString(R.string.export_to_supabase),
            getString(R.string.delete_deck)
        )

        MaterialAlertDialogBuilder(this)
            .setTitle(deck.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportDeckToSupabase(deck)
                    1 -> showDeleteDialog(deck)
                }
            }
            .show()
    }
    
    // Function to export a specific deck to Supabase
    private fun exportDeckToSupabase(deck: Deck) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@DeckActivity, "Exportando deck para o Supabase...", Toast.LENGTH_SHORT).show()
                
                // Export only this specific deck
                val result = exportSingleDeck(deck)
                
                if (result) {
                    Toast.makeText(
                        this@DeckActivity,
                        "Deck '${deck.name}' exportado com sucesso!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@DeckActivity,
                        "Erro ao exportar deck para o Supabase",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@DeckActivity,
                    "Erro ao exportar: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    // Helper function to export a single deck to Supabase
    private suspend fun exportSingleDeck(deck: Deck): Boolean = withContext(Dispatchers.IO) {
        try {
            // First retrieve all flashcards for this deck
            val flashcards = flashcardViewModel.getFlashcardsForDeckSync(deck.id)
            
            // Step 1: Export the deck
            val remoteDeck = syncManager.syncSingleDeckToRemote(deck)
            
            // Step 2: Export all flashcards in this deck if deck was exported successfully
            if (remoteDeck != null) {
                val remoteDeckId = remoteDeck.id
                
                // Export each flashcard
                flashcards.forEach { flashcard ->
                    // Update deckId to match the remote deck ID
                    val remoteFlashcard = flashcard.copy(deckId = remoteDeckId)
                    syncManager.syncSingleFlashcardToRemote(remoteFlashcard)
                }
                
                return@withContext true
            }
            
            return@withContext false
        } catch (e: Exception) {
            Log.e("DeckActivity", "Error exporting deck: ${e.message}")
            return@withContext false
        }
    }

    private fun showDeleteDialog(deck: Deck) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_deck))
            .setMessage(getString(R.string.delete_deck_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deckViewModel.delete(deck)
                flashcardViewModel.deleteAllFlashcardsForDeck(deck.id)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openFlashcardActivity(deck: Deck) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("deckId", deck.id)
        intent.putExtra("deckName", deck.name)
        startActivity(intent)
    }

    private fun addDeck(name: String, theme: String) {
        val deck = Deck(
            name = name,
            theme = theme,
            createdAt = System.currentTimeMillis()
        )
        deckViewModel.insert(deck)
        
        // Sincronizar imediatamente após criar um novo deck
        syncToRemote()
    }

    private fun updateDeck(deck: Deck, newName: String, newTheme: String) {
        val updatedDeck = deck.copy(name = newName, theme = newTheme)
        deckViewModel.update(updatedDeck)
        
        // Sincronizar imediatamente após atualizar um deck
        syncToRemote()
    }

    // Adicionar esta função para sincronizar com o servidor
    private fun syncToRemote() {
        lifecycleScope.launch {
            try {
                val syncManager = SyncManager(this@DeckActivity)
                val result = syncManager.syncToRemote(syncFlashcards = false, syncLocations = false)
                
                if (result) {
                    Toast.makeText(
                        this@DeckActivity,
                        "Deck sincronizado com o servidor",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@DeckActivity,
                        "Erro ao sincronizar com o servidor",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@DeckActivity,
                    "Erro na sincronização: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}