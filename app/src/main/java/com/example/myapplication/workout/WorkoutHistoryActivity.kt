package com.example.myapplication.workout

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.model.Workout
import com.example.myapplication.model.WorkoutSession
import java.text.SimpleDateFormat
import java.util.*

class WorkoutHistoryActivity : AppCompatActivity() {

    private val TAG = "WorkoutHistoryActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_history)

        val username = intent.getStringExtra("username") ?: ""
        Log.d(TAG, "Loading history for user: $username")

        val refreshButton = findViewById<Button>(R.id.refreshButton)
        val tabWorkouts = findViewById<Button>(R.id.tabWorkouts)
        val tabHistory = findViewById<Button>(R.id.tabHistory)
        val workoutsTab = findViewById<LinearLayout>(R.id.workoutsTab)
        val historyTab = findViewById<LinearLayout>(R.id.historyTab)

        // Инициализация данных
        refreshData(username)

        // Обработчики табов
        tabWorkouts.setOnClickListener {
            switchTab(true)
        }

        tabHistory.setOnClickListener {
            switchTab(false)
        }

        refreshButton.setOnClickListener {
            refreshData(username)
        }
    }

    private fun switchTab(showWorkouts: Boolean) {
        val tabWorkouts = findViewById<Button>(R.id.tabWorkouts)
        val tabHistory = findViewById<Button>(R.id.tabHistory)
        val workoutsTab = findViewById<LinearLayout>(R.id.workoutsTab)
        val historyTab = findViewById<LinearLayout>(R.id.historyTab)

        if (showWorkouts) {
            workoutsTab.visibility = View.VISIBLE
            historyTab.visibility = View.GONE
            tabWorkouts.setBackgroundResource(R.drawable.tab_selected)
            tabHistory.setBackgroundResource(R.drawable.tab_unselected)
            tabWorkouts.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            tabHistory.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            workoutsTab.visibility = View.GONE
            historyTab.visibility = View.VISIBLE
            tabWorkouts.setBackgroundResource(R.drawable.tab_unselected)
            tabHistory.setBackgroundResource(R.drawable.tab_selected)
            tabWorkouts.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            tabHistory.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    override fun onResume() {
        super.onResume()
        val username = intent.getStringExtra("username") ?: ""
        refreshData(username)
    }

    private fun refreshData(username: String) {
        Log.d(TAG, "Refreshing data for user: $username")

        val workoutsListView = findViewById<ListView>(R.id.workoutsListView)
        val historyListView = findViewById<ListView>(R.id.historyListView)

        // Обновляем список тренировок
        val workouts = WorkoutManager.getUserWorkouts(this, username)
        Log.d(TAG, "Loaded ${workouts.size} workouts for user $username")

        val workoutAdapter = WorkoutAdapter(workouts, username)
        workoutsListView.adapter = workoutAdapter

        // Обновляем историю
        val sessions = WorkoutManager.getUserWorkoutSessions(this, username)
        Log.d(TAG, "Loaded ${sessions.size} sessions for user $username")

        val historyAdapter = WorkoutHistoryAdapter(sessions)
        historyListView.adapter = historyAdapter

        // Показываем сообщение если история пуста
        if (sessions.isEmpty()) {
            Toast.makeText(this, "🎯 Выполните первую тренировку!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "✅ Данные обновлены", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSessionDetails(session: WorkoutSession) {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy 'в' HH:mm", Locale.getDefault())
        val durationMinutes = session.duration / 60000

        val details = StringBuilder()
        details.append("💪 Тренировка: ${session.workoutName}\n")
        details.append("📅 Дата: ${dateFormat.format(session.date)}\n")
        details.append("⏱️ Длительность: $durationMinutes минут\n\n")

        var completedExercises = 0
        session.exercises.forEach { exercise ->
            val completedSets = exercise.previousSets.size
            val totalSets = exercise.sets
            val exerciseCompleted = completedSets > 0

            if (exerciseCompleted) completedExercises++

            details.append("${if (exerciseCompleted) "✅" else "⭕"} ${exercise.name}:\n")
            if (completedSets > 0) {
                exercise.previousSets.forEachIndexed { index, set ->
                    details.append("   🔹 Подход ${index + 1}: ${set.weight}кг × ${set.reps}\n")
                }
            } else {
                details.append("   🔸 Подходы не выполнены\n")
            }
            details.append("   📊 Прогресс: $completedSets/$totalSets\n\n")
        }

        val completionPercent = (completedExercises.toDouble() / session.exercises.size * 100).toInt()
        details.append("\n🎯 Общее выполнение: $completionPercent% ($completedExercises/${session.exercises.size} упражнений)")

        AlertDialog.Builder(this)
            .setTitle("📋 Детали тренировки")
            .setMessage(details.toString())
            .setPositiveButton("👍 Понятно", null)
            .show()
    }

    private inner class WorkoutAdapter(
        private val workouts: List<Workout>,
        private val username: String
    ) : BaseAdapter() {

        override fun getCount(): Int = workouts.size

        override fun getItem(position: Int): Workout = workouts[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_workout, parent, false)

            val workout = getItem(position)
            val workoutNameTextView = view.findViewById<TextView>(R.id.workoutNameTextView)
            val workoutExercisesTextView = view.findViewById<TextView>(R.id.workoutExercisesTextView)
            val exerciseCountBadge = view.findViewById<TextView>(R.id.exerciseCountBadge)
            val startWorkoutButton = view.findViewById<Button>(R.id.startWorkoutButton)

            workoutNameTextView.text = workout.name
            val exerciseCount = workout.exercises.size
            workoutExercisesTextView.text = when (exerciseCount) {
                1 -> "1 упражнение"
                in 2..4 -> "$exerciseCount упражнения"
                else -> "$exerciseCount упражнений"
            }
            exerciseCountBadge.text = exerciseCount.toString()

            startWorkoutButton.setOnClickListener {
                Log.d(TAG, "Starting workout: ${workout.name}")
                val intent = Intent(this@WorkoutHistoryActivity, WorkoutSessionActivity::class.java)
                intent.putExtra("workoutName", workout.name)
                intent.putExtra("username", username)
                intent.putExtra("exercises", ArrayList(workout.exercises))
                startActivity(intent)
            }

            return view
        }
    }

    private inner class WorkoutHistoryAdapter(
        private val sessions: List<WorkoutSession>
    ) : BaseAdapter() {

        private val sortedSessions = sessions.sortedByDescending { it.date }

        override fun getCount(): Int = sortedSessions.size

        override fun getItem(position: Int): WorkoutSession = sortedSessions[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_workout_history, parent, false)

            val session = getItem(position)
            val workoutNameTextView = view.findViewById<TextView>(R.id.historyWorkoutNameTextView)
            val dateTextView = view.findViewById<TextView>(R.id.historyDateTextView)
            val durationTextView = view.findViewById<TextView>(R.id.historyDurationTextView)
            val exercisesTextView = view.findViewById<TextView>(R.id.historyExercisesTextView)
            val completionBadge = view.findViewById<TextView>(R.id.completionBadge)
            val progressBar = view.findViewById<View>(R.id.progressBar)
            val progressText = view.findViewById<TextView>(R.id.progressText)

            val dateFormat = SimpleDateFormat("dd MMMM yyyy 'в' HH:mm", Locale.getDefault())
            val durationMinutes = if (session.duration > 0) session.duration / 60000 else 0

            workoutNameTextView.text = session.workoutName
            dateTextView.text = dateFormat.format(session.date)
            durationTextView.text = "$durationMinutes мин"

            // Расчет прогресса
            val completedExercises = session.exercises.count { it.previousSets.isNotEmpty() }
            val totalExercises = session.exercises.size
            val completionPercent = if (totalExercises > 0) {
                (completedExercises.toDouble() / totalExercises * 100).toInt()
            } else {
                0
            }

            exercisesTextView.text = "$completedExercises/$totalExercises упр."
            completionBadge.text = "$completionPercent%"
            progressText.text = "$completionPercent%"

            // Настройка прогресс-бара
            val progressDrawable = GradientDrawable().apply {
                cornerRadius = 3f
                setColor(ContextCompat.getColor(parent.context, R.color.primary))
            }

            val layoutParams = progressBar.layoutParams
            layoutParams.width = (parent.width * 0.01 * completionPercent).toInt()
            progressBar.layoutParams = layoutParams
            progressBar.background = progressDrawable

            // Цвет бейджа в зависимости от прогресса
            val badgeColor = when {
                completionPercent >= 80 -> R.color.success
                completionPercent >= 50 -> R.color.warning
                else -> R.color.error
            }
            completionBadge.setBackgroundResource(badgeColor)

            view.setOnClickListener {
                showSessionDetails(session)
            }

            return view
        }
    }
}