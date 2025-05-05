package com.example.flashcards

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flashcards.data.SyncManager
import com.example.flashcards.data.remote.model.RemoteDeck
import com.example.flashcards.databinding.ActivityExerciseSelectionBinding
import com.example.flashcards.databinding.DialogRemoteDecksBinding
import com.example.flashcards.ui.DeckAdapter
import com.example.flashcards.ui.DeckViewModel
import com.example.flashcards.ui.RemoteDeckAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ExerciseSelectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExerciseSelectionBinding
    private lateinit var deckViewModel: DeckViewModel
    private lateinit var adapter: DeckAdapter
    private lateinit var syncManager: SyncManager
    private val TAG = "ExerciseSelectionActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Selecionar Deck para Exercício"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        syncManager = SyncManager(this)
        deckViewModel = ViewModelProvider(this)[DeckViewModel::class.java]
        setupRecyclerView()
        setupAllDecksButton()
        setupFindRemoteDecksButton()
        setupBottomNavigation()
        observeDecks()
    }

    private fun setupRecyclerView() {
        adapter = DeckAdapter(
            onItemClick = { deck -> startExercise(deck.id, deck.name) },
            getFlashcardCount = { deckId ->
                var count = 0
                lifecycleScope.launch {
                    val actualCount = deckViewModel.getFlashcardCountForDeck(deckId)
                    adapter.updateFlashcardCount(deckId, actualCount)
                }
                count
            },
            showEditButton = false // Desabilitar o botão de edição na tela de exercício
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

    private fun setupFindRemoteDecksButton() {
        binding.findRemoteDecksButton.setOnClickListener {
            showFindRemoteDecksDialog()
        }
    }

    private fun showFindRemoteDecksDialog() {
        val dialogBinding = DialogRemoteDecksBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        // Configurar o RecyclerView com o adapter para decks remotos
        val remoteDeckAdapter = RemoteDeckAdapter()
        dialogBinding.remoteDecksRecyclerView.adapter = remoteDeckAdapter
        dialogBinding.remoteDecksRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Mostrar o indicador de progresso enquanto busca os decks
        dialogBinding.progressBar.visibility = View.VISIBLE
        dialogBinding.remoteDecksRecyclerView.visibility = View.GONE
        dialogBinding.emptyView.visibility = View.GONE
        
        // Configurar botão de cancelar
        dialogBinding.cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Configurar botão de importar selecionados
        dialogBinding.importButton.setOnClickListener {
            val selectedDecks = remoteDeckAdapter.getSelectedDecks()
            if (selectedDecks.isEmpty()) {
                Toast.makeText(this, "Selecione ao menos um deck para importar", Toast.LENGTH_SHORT).show()
            } else {
                importRemoteDecks(selectedDecks, dialog)
            }
        }
        
        // Buscar decks disponíveis no Supabase
        fetchRemoteDecks(dialogBinding, remoteDeckAdapter)
        
        dialog.show()
    }
    
    private fun fetchRemoteDecks(dialogBinding: DialogRemoteDecksBinding, adapter: RemoteDeckAdapter) {
        Log.d(TAG, "Iniciando busca por decks remotos...")
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Chamando syncManager.fetchAvailableRemoteDecks()")
                
                // Mostrar Toast para informar que a busca está em andamento
                Toast.makeText(this@ExerciseSelectionActivity, "Buscando decks online...", Toast.LENGTH_SHORT).show()
                
                val remoteDecks = syncManager.fetchAvailableRemoteDecks()
                Log.d(TAG, "Busca concluída. Encontrados ${remoteDecks.size} decks remotos disponíveis")
                
                dialogBinding.progressBar.visibility = View.GONE
                
                if (remoteDecks.isEmpty()) {
                    Log.w(TAG, "Nenhum deck remoto disponível para importação")
                    dialogBinding.emptyView.visibility = View.VISIBLE
                    dialogBinding.remoteDecksRecyclerView.visibility = View.GONE
                    
                    // Toast informando que não há decks disponíveis
                    Toast.makeText(
                        this@ExerciseSelectionActivity, 
                        "Não foram encontrados decks online disponíveis para importação", 
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    dialogBinding.emptyView.visibility = View.GONE
                    dialogBinding.remoteDecksRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(remoteDecks)
                    
                    // Logar nomes dos decks encontrados para debug
                    remoteDecks.forEachIndexed { index, deck ->
                        Log.d(TAG, "Deck #$index disponível: ID=${deck.id}, Nome=${deck.name}, Tema=${deck.theme ?: "N/A"}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao buscar decks remotos: ${e.message}", e)
                dialogBinding.progressBar.visibility = View.GONE
                dialogBinding.emptyView.visibility = View.VISIBLE
                // Corrigindo a referência - acessando o TextView que é o segundo filho (índice 1) do LinearLayout
                val textView = (dialogBinding.emptyView.getChildAt(1) as? TextView)
                textView?.text = "Erro ao buscar decks: ${e.message}"
                Toast.makeText(this@ExerciseSelectionActivity, "Erro ao buscar decks: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun importRemoteDecks(decks: List<RemoteDeck>, dialog: Dialog) {
        // Mostrar diálogo de progresso
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Importando Decks")
            .setMessage("Aguarde enquanto os decks são importados...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        // Iniciar a importação em uma coroutine
        lifecycleScope.launch {
            try {
                val success = syncManager.importRemoteDecks(decks)
                progressDialog.dismiss()
                dialog.dismiss()
                
                if (success) {
                    Toast.makeText(
                        this@ExerciseSelectionActivity, 
                        "${decks.size} deck(s) importado(s) com sucesso!", 
                        Toast.LENGTH_LONG
                    ).show()
                    // Refresh da lista de decks se necessário
                    deckViewModel.refreshDecks()
                } else {
                    Toast.makeText(
                        this@ExerciseSelectionActivity,
                        "Não foi possível importar os decks selecionados",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao importar decks: ${e.message}", e)
                progressDialog.dismiss()
                Toast.makeText(
                    this@ExerciseSelectionActivity,
                    "Erro durante importação: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
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

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.navigation_exercise
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish() // Finalizando a atividade atual para evitar problemas de navegação
                    true
                }
                R.id.navigation_decks -> {
                    startActivity(Intent(this, DeckActivity::class.java))
                    finish() // Finalizando a atividade atual para evitar problemas de navegação
                    true
                }
                R.id.navigation_exercise -> true
                R.id.navigation_environments -> {
                    startActivity(Intent(this, EnvironmentsActivity::class.java))
                    finish() // Finalizando a atividade atual para evitar problemas de navegação
                    true
                }
                else -> false
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}