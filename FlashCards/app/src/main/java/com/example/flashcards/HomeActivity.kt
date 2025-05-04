package com.example.flashcards

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.flashcards.data.FlashcardDatabase
import com.example.flashcards.data.WeeklyStats
import com.example.flashcards.data.WeeklyStatsRepository
import com.example.flashcards.data.SyncManager
import com.example.flashcards.databinding.ActivityHomeBinding
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.net.UnknownHostException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var syncManager: SyncManager
    private lateinit var weeklyStatsRepository: WeeklyStatsRepository
    private var syncInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializando o gerenciador de sincronização
        syncManager = SyncManager(this)
        weeklyStatsRepository = WeeklyStatsRepository(FlashcardDatabase.getDatabase(this).weeklyStatsDao())

        setupBottomNavigation()
        setupStartStudyingButton()
        setupSyncButton()
        setupCharts()
        loadWeeklyStats()
        
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

    private fun setupCharts() {
        setupAccuracyChart()
        setupTypeChart()
    }

    private fun setupAccuracyChart() {
        val chart = binding.accuracyChart
        chart.description.isEnabled = false
        chart.setDrawHoleEnabled(true)
        chart.setHoleColor(Color.WHITE)
        chart.setTransparentCircleColor(Color.WHITE)
        chart.setTransparentCircleAlpha(110)
        chart.holeRadius = 58f
        chart.transparentCircleRadius = 61f
        chart.setDrawCenterText(true)
        chart.rotationAngle = 0f
        chart.isRotationEnabled = true
        chart.isHighlightPerTapEnabled = true
        chart.animateY(1000)
        chart.legend.isEnabled = false
    }

    private fun setupTypeChart() {
        val chart = binding.typeChart
        chart.description.isEnabled = false
        chart.setDrawHoleEnabled(true)
        chart.setHoleColor(Color.WHITE)
        chart.setTransparentCircleColor(Color.WHITE)
        chart.setTransparentCircleAlpha(110)
        chart.holeRadius = 58f
        chart.transparentCircleRadius = 61f
        chart.setDrawCenterText(true)
        chart.rotationAngle = 0f
        chart.isRotationEnabled = true
        chart.isHighlightPerTapEnabled = true
        chart.animateY(1000)
        chart.legend.isEnabled = false
    }

    private fun updateCharts(stats: WeeklyStats?) {
        if (stats == null) {
            updateAccuracyChart(0, 0)
            updateTypeChart(emptyMap())
            return
        }

        // Atualizar gráfico de acurácia
        updateAccuracyChart(stats.cardsCorrect, stats.cardsIncorrect)

        // Atualizar gráfico de tipos de flashcards
        val typeStats = mapOf(
            "FV" to stats.frontBackCount,
            "Omissão" to stats.clozeCount,
            "Texto" to stats.textInputCount,
            "ME" to stats.multipleChoiceCount,
            "Básico" to stats.basicCount
        )
        updateTypeChart(typeStats)
    }

    private fun updateAccuracyChart(correct: Int, incorrect: Int) {
        val entries = ArrayList<PieEntry>()
        if (correct > 0) entries.add(PieEntry(correct.toFloat(), "Acertos"))
        if (incorrect > 0) entries.add(PieEntry(incorrect.toFloat(), "Erros"))

        val dataSet = PieDataSet(entries, "")
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 11f
        dataSet.valueLineColor = Color.BLACK
        dataSet.valueLinePart1Length = 0.4f
        dataSet.valueLinePart2Length = 0.4f
        dataSet.setDrawValues(true)

        // Cores para acertos e erros
        val colors = ArrayList<Int>()
        colors.add(Color.parseColor("#4CAF50")) // Verde para acertos
        colors.add(Color.parseColor("#F44336")) // Vermelho para erros
        dataSet.colors = colors

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter())
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.BLACK)
        data.setValueTypeface(Typeface.DEFAULT_BOLD)

        binding.accuracyChart.data = data
        binding.accuracyChart.legend.textColor = Color.BLACK
        binding.accuracyChart.legend.typeface = Typeface.DEFAULT_BOLD
        binding.accuracyChart.setEntryLabelColor(Color.BLACK)
        binding.accuracyChart.setEntryLabelTextSize(12f)
        binding.accuracyChart.setEntryLabelTypeface(Typeface.DEFAULT_BOLD)
        binding.accuracyChart.invalidate()
    }

    private fun updateTypeChart(typeStats: Map<String, Int>) {
        val entries = ArrayList<PieEntry>()
        typeStats.forEach { (type, count) ->
            if (count > 0) { // Só adiciona tipos que foram usados
                entries.add(PieEntry(count.toFloat(), type))
            }
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 11f
        dataSet.valueLineColor = Color.BLACK
        dataSet.valueLinePart1Length = 0.4f
        dataSet.valueLinePart2Length = 0.4f
        dataSet.setDrawValues(true)

        // Cores para diferentes tipos de flashcards
        val colors = ArrayList<Int>()
        colors.add(Color.parseColor("#2196F3")) // Azul - Frente/Trás
        colors.add(Color.parseColor("#FF9800")) // Laranja - Cloze
        colors.add(Color.parseColor("#9C27B0")) // Roxo - Texto
        colors.add(Color.parseColor("#4CAF50")) // Verde - Múltipla Escolha
        colors.add(Color.parseColor("#F44336")) // Vermelho - Básico
        dataSet.colors = colors

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter())
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.BLACK)
        data.setValueTypeface(Typeface.DEFAULT_BOLD)

        binding.typeChart.data = data
        binding.typeChart.legend.textColor = Color.BLACK
        binding.typeChart.legend.typeface = Typeface.DEFAULT_BOLD
        binding.typeChart.setEntryLabelColor(Color.BLACK)
        binding.typeChart.setEntryLabelTextSize(12f)
        binding.typeChart.setEntryLabelTypeface(Typeface.DEFAULT_BOLD)
        binding.typeChart.invalidate()
    }

    private fun loadWeeklyStats() {
        lifecycleScope.launch {
            weeklyStatsRepository.getCurrentWeekStats().collect { stats ->
                updateStatsUI(stats)
                updateCharts(stats)
            }
        }
    }

    private fun updateStatsUI(stats: WeeklyStats?) {
        if (stats == null) {
            binding.cardsReviewedValue.text = "0"
            binding.cardsCorrectValue.text = "0"
            binding.cardsIncorrectValue.text = "0"
            binding.successRateValue.text = "0%"
            return
        }

        binding.cardsReviewedValue.text = stats.cardsReviewed.toString()
        binding.cardsCorrectValue.text = stats.cardsCorrect.toString()
        binding.cardsIncorrectValue.text = stats.cardsIncorrect.toString()

        val successRate = if (stats.cardsReviewed > 0) {
            (stats.cardsCorrect.toFloat() / stats.cardsReviewed.toFloat() * 100).toInt()
        } else {
            0
        }
        binding.successRateValue.text = "$successRate%"
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