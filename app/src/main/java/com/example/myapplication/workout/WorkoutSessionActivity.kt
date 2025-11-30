package com.example.myapplication.workout

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.model.Exercise
import com.example.myapplication.model.Set
import com.example.myapplication.model.WorkoutSession
import com.example.myapplication.voice.VoiceManager
import java.util.*

class WorkoutSessionActivity : AppCompatActivity(), VoiceManager.VoiceCallback {

    private val TAG = "WorkoutSessionActivity"
    private lateinit var chronometer: Chronometer
    private var startTime: Long = 0
    private var isRunning = false
    private val currentSets = mutableMapOf<String, MutableList<Set>>()
    private lateinit var exercises: List<Exercise>
    private lateinit var adapter: ExerciseAdapter

    // Голосовое управление
    private lateinit var voiceManager: VoiceManager
    private lateinit var voiceButton: Button
    private lateinit var voiceStatus: TextView
    private val RECORD_AUDIO_REQUEST_CODE = 101
    private var isWaitingForPermission = false
    private var currentExerciseForVoice: Exercise? = null
    private var currentPositionForVoice: Int = -1
    private var isVoiceInputMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_session)

        val workoutName = intent.getStringExtra("workoutName") ?: ""
        val username = intent.getStringExtra("username") ?: ""
        val exercisesList = intent.getSerializableExtra("exercises") as? ArrayList<Exercise> ?: arrayListOf()
        exercises = exercisesList

        Log.d(TAG, "Starting NEW workout session: $workoutName for user: $username with ${exercises.size} exercises")

        val workoutNameTextView = findViewById<TextView>(R.id.workoutNameTextView)
        val exercisesListView = findViewById<ListView>(R.id.exercisesListView)
        val startStopButton = findViewById<Button>(R.id.startStopButton)
        val finishButton = findViewById<Button>(R.id.finishButton)
        val voiceInputButton = findViewById<Button>(R.id.voiceInputButton)
        chronometer = findViewById(R.id.chronometer)

        // Голосовое управление
        voiceButton = findViewById<Button>(R.id.voiceButton)
        voiceStatus = findViewById<TextView>(R.id.voiceStatus)

        workoutNameTextView.text = "💪 $workoutName"

        // НАЧИНАЕМ С ЧИСТЫМИ ПОДХОДАМИ КАЖДЫЙ РАЗ
        exercises.forEach { exercise ->
            currentSets[exercise.name] = mutableListOf()
        }

        // Загружаем предыдущие результаты ТОЛЬКО для отображения истории
        val previousSession = WorkoutManager.getLastWorkoutSession(this, workoutName, username)
        Log.d(TAG, "Previous session for reference: ${previousSession?.workoutName}")

        adapter = ExerciseAdapter(exercises, currentSets, previousSession)
        exercisesListView.adapter = adapter

        exercisesListView.setOnItemClickListener { _, _, position, _ ->
            val exercise = exercises[position]
            showSetDialog(exercise, position)
        }

        startStopButton.setOnClickListener {
            toggleWorkout()
        }

        finishButton.setOnClickListener {
            finishWorkout(username, workoutName)
        }

        // Кнопка голосового ввода подходов
        voiceInputButton.setOnClickListener {
            if (checkAudioPermission()) {
                startVoiceInputMode()
            }
        }

        // Инициализация голосового управления
        setupVoiceControl()
    }

    private fun setupVoiceControl() {
        voiceManager = VoiceManager(this, this)

        voiceButton.setOnClickListener {
            if (checkAudioPermission()) {
                startVoiceListening()
            }
        }

        // Обновляем статус голосового управления
        updateVoiceStatus()
    }

    private fun checkAudioPermission(): Boolean {
        val permission = Manifest.permission.RECORD_AUDIO
        val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            // Показываем объяснение, если нужно
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                showPermissionExplanation()
            } else {
                // Запрашиваем разрешение
                ActivityCompat.requestPermissions(this, arrayOf(permission), RECORD_AUDIO_REQUEST_CODE)
            }
            return false
        }
        return true
    }

    private fun showPermissionExplanation() {
        AlertDialog.Builder(this)
            .setTitle("🎤 Разрешение на микрофон")
            .setMessage("Для голосового управления необходимо разрешение на использование микрофона. Это позволит вам:\n\n• Управлять тренировкой командами\n• Голосом вводить подходы и повторения\n• Быстро добавлять результаты\n\nМикрофон используется только во время нажатия на кнопки микрофона.")
            .setPositiveButton("Разрешить") { _, _ ->
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_REQUEST_CODE)
            }
            .setNegativeButton("Отмена") { _, _ ->
                Toast.makeText(this, "Голосовое управление недоступно без разрешения", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun startVoiceListening() {
        if (voiceManager.isAvailable()) {
            try {
                voiceManager.startListening()
                voiceButton.setBackgroundResource(R.drawable.voice_button_listening)
                isWaitingForPermission = false
                isVoiceInputMode = false
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                Toast.makeText(this, "❌ Ошибка безопасности. Перезапустите приложение.", Toast.LENGTH_SHORT).show()
                updateVoiceStatus()
            }
        } else {
            Toast.makeText(this, "❌ Голосовое управление не доступно на этом устройстве", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVoiceInputMode() {
        if (voiceManager.isAvailable()) {
            try {
                // Находим следующее упражнение для ввода
                val nextExercise = findNextExercise()
                if (nextExercise != null) {
                    currentExerciseForVoice = nextExercise
                    currentPositionForVoice = exercises.indexOf(nextExercise)
                    voiceManager.startListening()
                    findViewById<Button>(R.id.voiceInputButton).setBackgroundResource(R.drawable.voice_button_listening)
                    isVoiceInputMode = true
                    Toast.makeText(this, "🎤 Назовите вес и повторения для ${nextExercise.name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "✅ Все упражнения завершены!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                Toast.makeText(this, "❌ Ошибка безопасности", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "❌ Голосовое управление не доступно", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RECORD_AUDIO_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Разрешение получено
                    Log.d(TAG, "Разрешение на микрофон получено")
                    Toast.makeText(this, "✅ Разрешение на микрофон получено!", Toast.LENGTH_SHORT).show()
                    updateVoiceStatus()
                    // Автоматически запускаем прослушивание, если пользователь ждал
                    if (isWaitingForPermission) {
                        startVoiceListening()
                    }
                } else {
                    // Разрешение отклонено
                    Log.d(TAG, "Разрешение на микрофон отклонено")
                    Toast.makeText(this, "❌ Голосовое управление недоступно без разрешения", Toast.LENGTH_LONG).show()
                    updateVoiceStatus()
                }
                isWaitingForPermission = false
            }
        }
    }

    private fun toggleWorkout() {
        if (!isRunning) {
            startTime = SystemClock.elapsedRealtime()
            chronometer.base = startTime
            chronometer.start()
            findViewById<Button>(R.id.startStopButton).text = "⏸️ Пауза"
            isRunning = true
            Log.d(TAG, "Workout started")
            Toast.makeText(this, "🎯 Тренировка начата!", Toast.LENGTH_SHORT).show()
        } else {
            chronometer.stop()
            findViewById<Button>(R.id.startStopButton).text = "▶️ Продолжить"
            isRunning = false
            Log.d(TAG, "Workout paused")
            Toast.makeText(this, "⏸️ Тренировка на паузе", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishWorkout(username: String, workoutName: String) {
        chronometer.stop()
        val duration = if (isRunning) SystemClock.elapsedRealtime() - startTime else 0

        Log.d(TAG, "Finishing workout, duration: $duration ms")

        val completedExercises = exercises.map { exercise ->
            val sets = currentSets[exercise.name] ?: emptyList()
            Log.d(TAG, "Exercise: ${exercise.name}, completed sets: ${sets.size}")
            Exercise(exercise.name, exercise.sets, sets)
        }

        val session = WorkoutSession(
            workoutName,
            completedExercises,
            Date(),
            duration,
            username
        )

        Log.d(TAG, "Saving workout session...")
        WorkoutManager.saveWorkoutSession(this, session)
        Toast.makeText(this, "✅ Тренировка завершена и сохранена!", Toast.LENGTH_SHORT).show()
        finish()
    }

    // Реализация VoiceCallback
    override fun onVoiceCommand(command: String) {
        Log.d(TAG, "Обработка голосовой команды: $command")

        // Возвращаем обычный фон кнопок
        voiceButton.setBackgroundResource(R.drawable.voice_button_background)
        findViewById<Button>(R.id.voiceInputButton).setBackgroundResource(R.drawable.button_secondary)

        if (isVoiceInputMode) {
            // Режим голосового ввода подходов
            processVoiceInput(command)
        } else {
            // Режим управления тренировкой
            processControlCommand(command)
        }

        // Обновляем статус
        updateVoiceStatus()
    }

    private fun processControlCommand(command: String) {
        when {
            command.contains("старт") || command.contains("начать") || command.contains("поехали") -> {
                if (!isRunning) {
                    toggleWorkout()
                    Toast.makeText(this, "🎤 Голосовая команда: Старт!", Toast.LENGTH_SHORT).show()
                }
            }
            command.contains("стоп") || command.contains("остановить") || command.contains("пауза") -> {
                if (isRunning) {
                    toggleWorkout()
                    Toast.makeText(this, "🎤 Голосовая команда: Стоп!", Toast.LENGTH_SHORT).show()
                }
            }
            command.contains("завершить") || command.contains("конец") || command.contains("финиш") -> {
                val username = intent.getStringExtra("username") ?: ""
                val workoutName = intent.getStringExtra("workoutName") ?: ""
                finishWorkout(username, workoutName)
                Toast.makeText(this, "🎤 Голосовая команда: Завершить!", Toast.LENGTH_SHORT).show()
            }
            command.contains("далее") || command.contains("следующ") || command.contains("добавить") -> {
                // Автоматическое добавление подхода к первому незавершенному упражнению
                findNextExercise()?.let { exercise ->
                    val position = exercises.indexOf(exercise)
                    showSetDialog(exercise, position)
                    Toast.makeText(this, "🎤 Добавляем подход для ${exercise.name}", Toast.LENGTH_SHORT).show()
                }
            }
            command.contains("ввод") || command.contains("голосом") || command.contains("сказать") -> {
                // Переход в режим голосового ввода
                startVoiceInputMode()
            }
            else -> {
                Toast.makeText(this, "❓ Команда не распознана: $command", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processVoiceInput(command: String) {
        val exercise = currentExerciseForVoice
        if (exercise != null) {
            // Парсим голосовой ввод для извлечения веса и повторений
            val (weight, reps) = parseVoiceInput(command)

            if (reps > 0) {
                // Добавляем подход
                val sets = currentSets.getOrPut(exercise.name) { mutableListOf() }
                sets.add(Set(weight, reps))

                // Обновляем отображение
                adapter.notifyDataSetChanged()

                // Показываем результат
                val successMessage = if (weight > 0) {
                    "✅ ${exercise.name}: ${weight}кг × ${reps} повторений"
                } else {
                    "✅ ${exercise.name}: ${reps} повторений"
                }
                Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()

                // Автоматически переходим к следующему подходу или упражнению
                val currentSetNumber = sets.size
                val totalSets = exercise.sets

                if (currentSetNumber < totalSets) {
                    // Следующий подход того же упражнения
                    Handler().postDelayed({
                        Toast.makeText(this, "🎤 Подход ${currentSetNumber + 1}. Назовите вес и повторения", Toast.LENGTH_LONG).show()
                        startVoiceInputMode()
                    }, 1500)
                } else {
                    // Упражнение завершено, ищем следующее
                    Handler().postDelayed({
                        val nextExercise = findNextExercise()
                        if (nextExercise != null) {
                            currentExerciseForVoice = nextExercise
                            currentPositionForVoice = exercises.indexOf(nextExercise)
                            Toast.makeText(this, "🎤 ${nextExercise.name}. Назовите вес и повторения", Toast.LENGTH_LONG).show()
                            startVoiceInputMode()
                        } else {
                            Toast.makeText(this, "🎉 Все упражнения завершены!", Toast.LENGTH_LONG).show()
                            isVoiceInputMode = false
                        }
                    }, 1500)
                }
            } else {
                Toast.makeText(this, "❌ Не удалось распознать количество повторений. Попробуйте снова.", Toast.LENGTH_LONG).show()
                // Повторяем запрос
                Handler().postDelayed({
                    Toast.makeText(this, "🎤 Назовите вес и повторения для ${exercise.name}", Toast.LENGTH_LONG).show()
                    startVoiceInputMode()
                }, 1000)
            }
        }
        isVoiceInputMode = false
    }

    private fun parseVoiceInput(input: String): Pair<Double, Int> {
        var weight = 0.0
        var reps = 0

        // Убираем лишние слова и приводим к нижнему регистру
        val cleanInput = input.lowercase(Locale.getDefault())
            .replace("килограмм", "кг")
            .replace("килограммов", "кг")
            .replace("кг", " ")
            .replace("кило", " ")
            .replace("повторен", " ")
            .replace("раз", " ")
            .replace("и", " ")
            .replace("на", " ")
            .replace("по", " ")
            .replace("с", " ")

        Log.d(TAG, "Очищенный ввод: $cleanInput")

        // Ищем числа в строке
        val numbers = Regex("\\d+(\\.\\d+)?").findAll(cleanInput).map { it.value }.toList()

        when (numbers.size) {
            1 -> {
                // Только одно число - считаем его повторениями
                reps = numbers[0].toIntOrNull() ?: 0
            }
            2 -> {
                // Два числа - первое вес, второе повторения
                weight = numbers[0].toDoubleOrNull() ?: 0.0
                reps = numbers[1].toIntOrNull() ?: 0
            }
            else -> {
                // Пытаемся найти числа в разных форматах
                if (cleanInput.contains("сто") || cleanInput.contains("сотня")) {
                    weight = 100.0
                }
                // Ищем повторения по ключевым словам
                when {
                    cleanInput.contains("десять") || cleanInput.contains("10") -> reps = 10
                    cleanInput.contains("пятнадцать") || cleanInput.contains("15") -> reps = 15
                    cleanInput.contains("двадцать") || cleanInput.contains("20") -> reps = 20
                    cleanInput.contains("двенадцать") || cleanInput.contains("12") -> reps = 12
                    cleanInput.contains("восемь") || cleanInput.contains("8") -> reps = 8
                    cleanInput.contains("пять") || cleanInput.contains("5") -> reps = 5
                    else -> {
                        // Последняя попытка - берем первое найденное число как повторения
                        val firstNumber = Regex("\\d+").find(cleanInput)?.value?.toIntOrNull()
                        reps = firstNumber ?: 0
                    }
                }
            }
        }

        Log.d(TAG, "Распознано: вес=$weight, повторения=$reps")
        return Pair(weight, reps)
    }

    private fun findNextExercise(): Exercise? {
        return exercises.find { exercise ->
            val completedSets = currentSets[exercise.name]?.size ?: 0
            completedSets < exercise.sets
        }
    }

    override fun onVoiceError(error: String) {
        Log.e(TAG, "Ошибка голосового управления: $error")
        voiceStatus.text = "❌ Ошибка: $error"
        voiceButton.setBackgroundResource(R.drawable.voice_button_background)
        findViewById<Button>(R.id.voiceInputButton).setBackgroundResource(R.drawable.button_secondary)

        // Не показываем Toast для обычных ошибок (например, "нет совпадений")
        if (!error.contains("нет совпадений") && !error.contains("таймаут")) {
            Toast.makeText(this, "🎤 Ошибка: $error", Toast.LENGTH_SHORT).show()
        }

        isVoiceInputMode = false
    }

    override fun onVoiceReady() {
        voiceStatus.text = if (isVoiceInputMode) "🎤 Говорите вес и повторения..." else "🎤 Готов к командам..."
        val message = if (isVoiceInputMode) {
            "🎤 Назовите вес и повторения"
        } else {
            "🎤 Слушаю команды..."
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onVoiceListening() {
        voiceStatus.text = "🎤 Слушаю..."
    }

    private fun updateVoiceStatus() {
        val permissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val status = when {
            !voiceManager.isAvailable() -> "❌ Голосовое управление не доступно"
            !permissionGranted -> "⚠️ Нажмите для запроса разрешения"
            else -> "🎤 Нажмите для голосовых команд"
        }
        voiceStatus.text = status

        // Обновляем доступность кнопок
        voiceButton.isEnabled = voiceManager.isAvailable()
        findViewById<Button>(R.id.voiceInputButton).isEnabled = voiceManager.isAvailable() && permissionGranted
    }

    override fun onResume() {
        super.onResume()
        // Обновляем статус при возвращении на экран
        updateVoiceStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
    }

    private fun showSetDialog(exercise: Exercise, position: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set, null)
        val weightEditText = dialogView.findViewById<EditText>(R.id.weightEditText)
        val repsEditText = dialogView.findViewById<EditText>(R.id.repsEditText)
        val currentSetNumber = (currentSets[exercise.name]?.size ?: 0) + 1

        AlertDialog.Builder(this)
            .setTitle("Подход $currentSetNumber для ${exercise.name}")
            .setView(dialogView)
            .setPositiveButton("Добавить") { _, _ ->
                val weight = weightEditText.text.toString().toDoubleOrNull() ?: 0.0
                val reps = repsEditText.text.toString().toIntOrNull() ?: 0

                if (reps > 0) {
                    val sets = currentSets.getOrPut(exercise.name) { mutableListOf() }
                    sets.add(Set(weight, reps))
                    Log.d(TAG, "Added set $currentSetNumber for ${exercise.name}: $weight kg x $reps reps")

                    // Обновляем отображение
                    adapter.notifyDataSetChanged()

                    // Показываем успешное сообщение
                    Toast.makeText(this, "✅ Подход $currentSetNumber добавлен!", Toast.LENGTH_SHORT).show()

                    // Автоматически открываем следующий подход, если это не последний
                    val totalSets = exercise.sets
                    if (currentSetNumber < totalSets) {
                        showSetDialog(exercise, position)
                    }
                } else {
                    Toast.makeText(this, "❌ Введите корректное количество повторений", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private inner class ExerciseAdapter(
        private val exercises: List<Exercise>,
        private val currentSets: Map<String, List<Set>>,
        private val previousSession: WorkoutSession?
    ) : BaseAdapter() {

        override fun getCount(): Int = exercises.size

        override fun getItem(position: Int): Exercise = exercises[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val context = parent.context
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_exercise, parent, false)

            val exercise = getItem(position)
            val exerciseNameTextView = view.findViewById<TextView>(R.id.exerciseNameTextView)
            val setsInfoTextView = view.findViewById<TextView>(R.id.setsInfoTextView)
            val currentSetsTextView = view.findViewById<TextView>(R.id.currentSetsTextView)
            val previousSetsTextView = view.findViewById<TextView>(R.id.previousSetsTextView)
            val progressContainer = view.findViewById<LinearLayout>(R.id.progressContainer)

            // Название упражнения и общее количество подходов
            exerciseNameTextView.text = "${exercise.name} (${exercise.sets} подходов)"

            // Текущие подходы (сегодня)
            val currentExerciseSets = currentSets[exercise.name] ?: emptyList()
            val completedSets = currentExerciseSets.size
            val totalSets = exercise.sets

            setsInfoTextView.text = "Выполнено: $completedSets/$totalSets"

            // Прогресс-бар визуальный
            progressContainer.removeAllViews()
            for (i in 0 until totalSets) {
                val progressView = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 12, 1f).apply {
                        marginEnd = 4
                    }
                    background = ContextCompat.getDrawable(
                        context,
                        if (i < completedSets) R.drawable.progress_completed
                        else R.drawable.progress_pending
                    )
                }
                progressContainer.addView(progressView)
            }

            // Текущие результаты
            if (currentExerciseSets.isNotEmpty()) {
                currentSetsTextView.text = "Сегодня: " + currentExerciseSets.joinToString(" | ") {
                    "${it.weight}кг × ${it.reps}"
                }
                currentSetsTextView.visibility = View.VISIBLE
            } else {
                currentSetsTextView.visibility = View.GONE
            }

            // Предыдущие результаты (только для информации)
            val previousExerciseSets = previousSession?.exercises?.find { it.name == exercise.name }?.previousSets ?: emptyList()
            if (previousExerciseSets.isNotEmpty()) {
                previousSetsTextView.text = "Прошлый раз: " + previousExerciseSets.joinToString(" | ") {
                    "${it.weight}кг × ${it.reps}"
                }
                previousSetsTextView.visibility = View.VISIBLE
            } else {
                previousSetsTextView.visibility = View.GONE
            }

            // Цвет фона в зависимости от прогресса
            val backgroundColor = when {
                completedSets == totalSets -> android.R.color.holo_green_light
                completedSets > 0 -> android.R.color.holo_orange_light
                else -> android.R.color.background_light
            }
            view.setBackgroundColor(ContextCompat.getColor(context, backgroundColor))

            return view
        }
    }
}