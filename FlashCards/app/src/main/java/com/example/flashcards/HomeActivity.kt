package com.example.flashcards

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.flashcards.data.SyncManager
import com.example.flashcards.databinding.ActivityHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.net.UnknownHostException

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var syncManager: SyncManager
    private var syncInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializando o gerenciador de sincronização
        syncManager = SyncManager(this)

        setupBottomNavigation()
        setupStartStudyingButton()
        setupSyncButton()
        
        // Verificar se deve iniciar sincronização automática
        if (intent.getBooleanExtra("showSync", false)) {
            syncData()
        }

        // Adicione a permissão de internet no manifest se ainda não tiver
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.navigation_home
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.navigation_home -> true
                R.id.navigation_decks -> {
                    startActivity(Intent(this, DeckActivity::class.java))
                    finish() // Finalizando a atividade atual para evitar problemas de navegação
                    true
                }
                R.id.navigation_exercise -> {
                    startActivity(Intent(this, ExerciseSelectionActivity::class.java))
                    finish() // Finalizando a atividade atual para evitar problemas de navegação
                    true
                }
                R.id.navigation_environments -> {
                    startActivity(Intent(this, EnvironmentsActivity::class.java))
                    finish() // Finalizando a atividade atual para evitar problemas de navegação
                    true
                }
                R.id.navigation_sync -> {
                    syncData()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupStartStudyingButton() {
        binding.startStudyingButton.setOnClickListener {
            startActivity(Intent(this, DeckActivity::class.java))
            finish() // Finalizando a atividade atual para não manter a pilha de navegação
        }
    }

    private fun setupSyncButton() {
        binding.syncButton.setOnClickListener {
            syncData()
        }
    }

    private fun syncData() {
        if (syncInProgress) {
            Toast.makeText(this, "Sincronização já em andamento", Toast.LENGTH_SHORT).show()
            return
        }

        syncInProgress = true
        binding.syncProgressBar.visibility = View.VISIBLE
        binding.syncButton.isEnabled = false

        // Exibir mensagem de sincronização em andamento
        Toast.makeText(this, "Iniciando sincronização...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                // Etapa 1: Upload de dados locais para o servidor
                val toRemoteSuccess = syncManager.syncToRemote()
                if (toRemoteSuccess) {
                    try {
                        // Etapa 2: Download de dados do servidor
                        val fromRemoteSuccess = syncManager.syncFromRemote()
                        if (fromRemoteSuccess) {
                            showSuccessDialog("Sincronização concluída com sucesso!\n\nLembre-se de verificar os decks para confirmar que os dados foram sincronizados corretamente.")
                        } else {
                            // Erro específico quando a sincronização "from remote" falhou
                            showErrorDialog("Erro ao receber dados do servidor. Os decks foram enviados, mas houve problema ao baixar os flashcards.")
                        }
                    } catch (e: Exception) {
                        val message = when {
                            e.message?.contains("sincronizar flashcards") == true -> 
                                "Erro ao sincronizar flashcards. Os dados podem estar parcialmente sincronizados."
                            e.message?.contains("conexão") == true -> 
                                "Erro de conexão ao receber dados. Verifique sua internet."
                            e.message?.contains("servidor") == true -> 
                                "Erro no servidor durante a sincronização. Tente novamente mais tarde."
                            else -> "Erro ao receber dados: ${e.message}"
                        }
                        showErrorDialog(message)
                    }
                } else {
                    // Erro específico quando a sincronização "to remote" falhou
                    showErrorDialog("Erro ao enviar dados para o servidor. Verifique sua conexão e tente novamente.")
                }
            } catch (e: UnknownHostException) {
                showErrorDialog("Não foi possível conectar ao servidor. Verifique sua conexão com a internet.")
            } catch (e: Exception) {
                val errorMsg = when {
                    e.message?.contains("timeout") == true -> 
                        "Tempo esgotado na conexão com o servidor. A rede pode estar lenta."
                    e.message?.contains("401") == true -> 
                        "Erro de autenticação. Verifique suas credenciais de acesso ao Supabase."
                    e.message?.contains("403") == true -> 
                        "Acesso negado ao servidor. Suas permissões podem ser insuficientes."
                    e.message?.contains("404") == true -> 
                        "Recurso não encontrado no servidor. Verifique a configuração do Supabase."
                    e.message?.contains("422") == true -> 
                        "Dados inválidos para o servidor. Formato de requisição incorreto."
                    e.message?.contains("500") == true -> 
                        "Erro interno no servidor Supabase. Tente novamente mais tarde."
                    else -> "Erro na sincronização: ${e.message}"
                }
                showErrorDialog(errorMsg)
            } finally {
                syncInProgress = false
                binding.syncProgressBar.visibility = View.INVISIBLE
                binding.syncButton.isEnabled = true
            }
        }
    }
    
    private fun showSuccessDialog(message: String) {
        runOnUiThread {
            MaterialAlertDialogBuilder(this)
                .setTitle("Sincronização Concluída")
                .setMessage(message)
                .setPositiveButton("OK") { _, _ ->
                    // Após sincronização bem-sucedida, recarregar a atividade para mostrar os dados atualizados
                    recreate()
                }
                .setIcon(R.drawable.ic_launcher_foreground)
                .show()
        }
    }
    
    private fun showErrorDialog(message: String) {
        runOnUiThread {
            MaterialAlertDialogBuilder(this)
                .setTitle("Erro de Sincronização")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setNegativeButton("Ver Decks") { _, _ ->
                    // Oferecer opção de ir para a tela de decks após erro, para verificar o que foi sincronizado
                    startActivity(Intent(this, DeckActivity::class.java))
                }
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
        }
    }
}