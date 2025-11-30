package com.example.myapplication.workout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.model.Exercise

class CreateWorkoutActivity : AppCompatActivity() {

    private val exercises = mutableListOf<Exercise>()
    private lateinit var adapter: ExerciseListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_workout)

        val workoutNameEditText = findViewById<EditText>(R.id.workoutNameEditText)
        val exerciseNameEditText = findViewById<EditText>(R.id.exerciseNameEditText)
        val setsEditText = findViewById<EditText>(R.id.setsEditText)
        val addExerciseButton = findViewById<Button>(R.id.addExerciseButton)
        val saveWorkoutButton = findViewById<Button>(R.id.saveWorkoutButton)
        val exercisesListView = findViewById<ListView>(R.id.exercisesListView)

        adapter = ExerciseListAdapter(exercises)
        exercisesListView.adapter = adapter

        addExerciseButton.setOnClickListener {
            val exerciseName = exerciseNameEditText.text.toString().trim()
            val setsText = setsEditText.text.toString().trim()

            if (exerciseName.isNotEmpty() && setsText.isNotEmpty()) {
                val sets = setsText.toIntOrNull() ?: 0
                if (sets > 0) {
                    exercises.add(Exercise(exerciseName, sets))
                    adapter.notifyDataSetChanged()

                    // Очищаем поля и показываем сообщение
                    exerciseNameEditText.text.clear()
                    setsEditText.text.clear()
                    Toast.makeText(this, "✅ Упражнение добавлено!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ Введите корректное количество подходов", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "❌ Заполните все поля", Toast.LENGTH_SHORT).show()
            }
        }

        exercisesListView.setOnItemClickListener { _, _, position, _ ->
            val removedExercise = exercises[position]
            exercises.removeAt(position)
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "🗑️ Удалено: ${removedExercise.name}", Toast.LENGTH_SHORT).show()
        }

        saveWorkoutButton.setOnClickListener {
            val workoutName = workoutNameEditText.text.toString().trim()
            val username = intent.getStringExtra("username") ?: ""

            if (workoutName.isNotEmpty() && exercises.isNotEmpty()) {
                val workout = com.example.myapplication.model.Workout(workoutName, exercises.toList(), username)
                WorkoutManager.saveWorkout(this, workout)

                Toast.makeText(this, "✅ Тренировка сохранена!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                if (workoutName.isEmpty()) {
                    Toast.makeText(this, "❌ Введите название тренировки", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "❌ Добавьте хотя бы одно упражнение", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private inner class ExerciseListAdapter(
        private val exercises: List<Exercise>
    ) : BaseAdapter() {

        override fun getCount(): Int = exercises.size

        override fun getItem(position: Int): Exercise = exercises[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exercise_list, parent, false)

            val exercise = getItem(position)
            val exerciseNameText = view.findViewById<TextView>(R.id.exerciseNameText)
            val exerciseSetsText = view.findViewById<TextView>(R.id.exerciseSetsText)

            exerciseNameText.text = exercise.name
            exerciseSetsText.text = when (exercise.sets) {
                1 -> "1 подход"
                in 2..4 -> "${exercise.sets} подхода"
                else -> "${exercise.sets} подходов"
            }

            return view
        }
    }
}