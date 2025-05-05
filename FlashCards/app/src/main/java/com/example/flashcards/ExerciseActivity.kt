package com.example.flashcards

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.flashcards.data.Flashcard
import com.example.flashcards.data.FlashcardType
import com.example.flashcards.data.FlashcardDatabase
import com.example.flashcards.data.WeeklyStatsRepository
import com.example.flashcards.databinding.ActivityExerciseBinding
import com.example.flashcards.ui.FlashcardViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val deckName = inputData.getString("deckName") ?: "Exercício"
        val channelId = "reminder_channel"
        val notificationId = 2

        // Cria o canal de notificação (necessário para Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Lembretes de Revisão"
            val descriptionText = "Notificações para refazer exercícios"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Intent para abrir o app ao clicar na notificação
        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // Monta a notificação
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Troque por um ícone do seu projeto
            .setContentTitle("Hora de revisar!")
            .setContentText("Refaça o exercício: $deckName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(notificationId, builder.build())
        }

        return Result.success()
    }
}



class ExerciseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExerciseBinding
    private lateinit var viewModel: FlashcardViewModel
    private lateinit var weeklyStatsRepository: WeeklyStatsRepository
    private var currentDeckId: Long = -1
    private var currentDeckName: String = ""
    private var flashcards: List<Flashcard> = emptyList()
    private var currentIndex: Int = 0
    private var correctAnswers: Int = 0
    private var wrongAnswers: Int = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Solicita permissão para notificações (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        currentDeckId = intent.getLongExtra("deckId", -1)
        currentDeckName = intent.getStringExtra("deckName") ?: ""
        supportActionBar?.title = "Exercício: $currentDeckName"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        viewModel = ViewModelProvider(this)[FlashcardViewModel::class.java]
        weeklyStatsRepository = WeeklyStatsRepository(FlashcardDatabase.getDatabase(this).weeklyStatsDao())
        setupExercise()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Menu removido conforme solicitado
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    private fun shuffleFlashcards() {
        flashcards = flashcards.shuffled()
        if (flashcards.isNotEmpty()) {
            currentIndex = 0
            correctAnswers = 0
            wrongAnswers = 0
            showCurrentFlashcard()
        }
    }

    private fun setupExercise() {
        lifecycleScope.launch {
            if (currentDeckId != -1L) {
                viewModel.getFlashcardsForDeckByReview(currentDeckId).collectLatest { flashcardList ->
                    flashcards = flashcardList.shuffled()
                    if (flashcards.isNotEmpty()) {
                        showCurrentFlashcard()
                    } else {
                        showEmptyState()
                    }
                }
            } else {
                viewModel.allFlashcardsByReview.collectLatest { flashcardList ->
                    flashcards = flashcardList.shuffled()
                    if (flashcards.isNotEmpty()) {
                        showCurrentFlashcard()
                    } else {
                        showEmptyState()
                    }
                }
            }
        }
    }

    private fun showEmptyState() {
        binding.exerciseContainer.visibility = View.GONE
        binding.emptyView.root.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE  // Esconde a barra de progresso no estado vazio
        binding.counterText.visibility = View.GONE  // Esconde o contador também
    }

    private fun showCurrentFlashcard() {
        binding.exerciseContainer.visibility = View.VISIBLE
        binding.emptyView.root.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE  // Mostra a barra de progresso durante o exercício
        binding.counterText.visibility = View.VISIBLE  // Mostra o contador durante o exercício

        val flashcard = flashcards[currentIndex]
        val progress = ((currentIndex + 1) * 100) / flashcards.size
        binding.progressBar.progress = progress
        binding.counterText.text = "${currentIndex + 1}/${flashcards.size}"
        binding.questionText.text = flashcard.front

        when (flashcard.type) {
            FlashcardType.FRONT_BACK -> setupFrontBackLayout(flashcard)
            FlashcardType.CLOZE -> setupClozeLayout(flashcard)
            FlashcardType.TEXT_INPUT -> setupTextInputLayout(flashcard)
            FlashcardType.MULTIPLE_CHOICE -> setupMultipleChoiceLayout(flashcard)
            FlashcardType.BASIC -> setupFrontBackLayout(flashcard)
        }
    }

    private fun setupFrontBackLayout(flashcard: Flashcard) {
        binding.clozeLayout.visibility = View.GONE
        binding.textInputLayout.visibility = View.GONE
        binding.multipleChoiceLayout.visibility = View.GONE
        binding.frontBackLayout.visibility = View.VISIBLE

        binding.answerInput.text?.clear()
        binding.answerInput.hint = "Digite a resposta"

        binding.submitButton.setOnClickListener {
            checkAnswer(flashcard, binding.answerInput.text?.toString() ?: "")
        }
    }
    private fun scheduleReminderNotification(deckName: String, correctPercent: Int) {
        val delayMillis = when {
            correctPercent >= 100 -> TimeUnit.HOURS.toMillis(1)
            correctPercent >= 80 -> TimeUnit.MINUTES.toMillis(40)
            correctPercent >= 50 -> TimeUnit.MINUTES.toMillis(2)
            correctPercent >= 10 -> TimeUnit.MINUTES.toMillis(1)
            else -> TimeUnit.SECONDS.toMillis(30)
        }

        val inputData = Data.Builder()
            .putString("deckName", deckName)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun setupClozeLayout(flashcard: Flashcard) {
        binding.frontBackLayout.visibility = View.GONE
        binding.textInputLayout.visibility = View.GONE
        binding.multipleChoiceLayout.visibility = View.GONE
        binding.clozeLayout.visibility = View.VISIBLE

        binding.clozeAnswerInput.text?.clear()
        binding.clozeAnswerInput.hint = "Digite a palavra que falta"

        binding.submitButton.setOnClickListener {
            checkAnswer(flashcard, binding.clozeAnswerInput.text?.toString() ?: "")
        }
    }

    private fun setupTextInputLayout(flashcard: Flashcard) {
        binding.frontBackLayout.visibility = View.GONE
        binding.clozeLayout.visibility = View.GONE
        binding.multipleChoiceLayout.visibility = View.GONE
        binding.textInputLayout.visibility = View.VISIBLE

        binding.textInputAnswer.text?.clear()
        binding.textInputAnswer.hint = "Digite a resposta"

        binding.submitButton.setOnClickListener {
            checkAnswer(flashcard, binding.textInputAnswer.text?.toString() ?: "")
        }
    }

    private fun setupMultipleChoiceLayout(flashcard: Flashcard) {
        binding.frontBackLayout.visibility = View.GONE
        binding.clozeLayout.visibility = View.GONE
        binding.textInputLayout.visibility = View.GONE
        binding.multipleChoiceLayout.visibility = View.VISIBLE

        binding.optionsRadioGroup.clearCheck()
        binding.optionsRadioGroup.removeAllViews()

        // Parse options from JSON string
        val optionsList = flashcard.options?.let { optionsJson ->
            try {
                Gson().fromJson<List<String>>(optionsJson, object : TypeToken<List<String>>() {}.type)
            } catch (e: Exception) {
                listOf<String>()
            }
        } ?: listOf<String>()
        
        for (i in optionsList.indices) {
                val radioButton = RadioButton(this)
                radioButton.id = View.generateViewId()
            radioButton.text = optionsList[i]
                binding.optionsRadioGroup.addView(radioButton)
        }

        binding.submitButton.setOnClickListener {
            val selectedId = binding.optionsRadioGroup.checkedRadioButtonId
            if (selectedId != -1) {
                val selectedIndex = binding.optionsRadioGroup.indexOfChild(findViewById(selectedId))
                checkAnswer(flashcard, selectedIndex.toString())
            } else {
                showError("Por favor, selecione uma opção")
            }
        }
    }

    private fun checkAnswer(flashcard: Flashcard, userAnswer: String) {
        val isCorrect = when (flashcard.type) {
            FlashcardType.FRONT_BACK -> userAnswer.equals(flashcard.back, ignoreCase = true)
            FlashcardType.CLOZE -> userAnswer.equals(flashcard.clozeAnswer, ignoreCase = true)
            FlashcardType.TEXT_INPUT -> userAnswer.equals(flashcard.back, ignoreCase = true)
            FlashcardType.MULTIPLE_CHOICE -> userAnswer.toInt() == flashcard.correctOptionIndex
            FlashcardType.BASIC -> userAnswer.equals(flashcard.back, ignoreCase = true)
        }

        updateStats(flashcard, isCorrect)

        if (isCorrect) {
            correctAnswers++
            showCorrectFeedback()
        } else {
            wrongAnswers++
            showWrongFeedback(flashcard)
        }
    }

    private fun updateStats(flashcard: Flashcard, cardCorrect: Boolean) {
        lifecycleScope.launch {
            weeklyStatsRepository.updateStats(flashcard.type, cardCorrect)
        }
    }

    private fun showCorrectFeedback() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Correto!")
            .setMessage("Parabéns! Você acertou!")
            .setPositiveButton("Próximo") { _, _ ->
                moveToNextFlashcard()
            }
            .setCancelable(false)
            .show()
    }

    private fun showWrongFeedback(flashcard: Flashcard) {
        val correctAnswer = when (flashcard.type) {
            FlashcardType.FRONT_BACK -> flashcard.back
            FlashcardType.CLOZE -> flashcard.clozeAnswer
            FlashcardType.TEXT_INPUT -> flashcard.back
            FlashcardType.MULTIPLE_CHOICE -> {
                // Parse options from JSON string and get the correct one
                val optionsList = flashcard.options?.let { optionsJson ->
                    try {
                        Gson().fromJson<List<String>>(optionsJson, object : TypeToken<List<String>>() {}.type)
                    } catch (e: Exception) {
                        listOf<String>()
                    }
                } ?: listOf<String>()
                optionsList.getOrElse(flashcard.correctOptionIndex ?: 0) { "" }
            }
            FlashcardType.BASIC -> flashcard.back
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Incorreto!")
            .setMessage("A resposta correta era: $correctAnswer")
            .setPositiveButton("Próximo") { _, _ ->
                moveToNextFlashcard()
            }
            .setCancelable(false)
            .show()
    }

    private fun moveToNextFlashcard() {
        currentIndex++
        if (currentIndex < flashcards.size) {
            showCurrentFlashcard()
        } else {
            showExerciseResults()
        }
    }

    private fun showExerciseResults() {
        showCompletionNotification(correctAnswers, flashcards.size)
        val percent = (correctAnswers * 100) / flashcards.size
        scheduleReminderNotification(currentDeckName, percent)
        MaterialAlertDialogBuilder(this)
            .setTitle("Exercício Concluído!!!!!!")
            .setMessage("Você acertou $correctAnswers de ${flashcards.size} flashcards.\n" +
                    "Acertos: $correctAnswers\n" +
                    "Erros: $wrongAnswers")
            .setPositiveButton("Concluir") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Erro")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun showCompletionNotification(correct: Int, total: Int) {
        val channelId = "exercise_results_channel"
        val notificationId = 1

        // Cria o canal de notificação (necessário para Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Resultados do Exercício"
            val descriptionText = "Notificações de conclusão de exercícios"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // Intent para abrir o app ao clicar na notificação
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // Monta a notificação
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use um ícone do seu projeto
            .setContentTitle("Exercício concluído!")
            .setContentText("Você acertou $correct de $total flashcards.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
    }
}