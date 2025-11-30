package com.example.myapplication.workout

import android.content.Context
import android.util.Log
import com.example.myapplication.model.Workout
import com.example.myapplication.model.WorkoutSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*

class WorkoutManager {

    companion object {
        private const val TAG = "WorkoutManager"
        private const val WORKOUTS_FILE = "workouts.json"
        private const val SESSIONS_FILE = "sessions.json"
        private val gson = Gson()

        fun saveWorkout(context: Context, workout: Workout) {
            val workouts = getUserWorkouts(context, workout.createdBy).toMutableList()
            workouts.removeAll { it.name == workout.name }
            workouts.add(workout)
            saveToFile(context, "$WORKOUTS_FILE${workout.createdBy}", workouts)
            Log.d(TAG, "Workout saved: ${workout.name} for user ${workout.createdBy}")
        }

        fun getUserWorkouts(context: Context, username: String): List<Workout> {
            val workouts = loadFromFile<Workout>(context, "$WORKOUTS_FILE$username") ?: emptyList()
            Log.d(TAG, "Loaded ${workouts.size} workouts for user $username")
            return workouts
        }

        fun saveWorkoutSession(context: Context, session: WorkoutSession) {
            Log.d(TAG, "Saving workout session: ${session.workoutName} for user ${session.username}")

            val sessions = getUserWorkoutSessions(context, session.username).toMutableList()
            sessions.add(session)

            val success = saveToFile(context, "$SESSIONS_FILE${session.username}", sessions)
            if (success) {
                Log.d(TAG, "Workout session saved successfully: ${session.workoutName}")

                // Проверим, что сохранилось
                val savedSessions = getUserWorkoutSessions(context, session.username)
                Log.d(TAG, "Now have ${savedSessions.size} sessions for user ${session.username}")
            } else {
                Log.e(TAG, "Failed to save workout session")
            }
        }

        fun getUserWorkoutSessions(context: Context, username: String): List<WorkoutSession> {
            val sessions = loadFromFile<WorkoutSession>(context, "$SESSIONS_FILE$username") ?: emptyList()
            Log.d(TAG, "Loaded ${sessions.size} sessions for user $username")
            sessions.forEach { session ->
                Log.d(TAG, "Session: ${session.workoutName} - ${session.date} - ${session.exercises.size} exercises")
            }
            return sessions
        }

        fun getLastWorkoutSession(context: Context, workoutName: String, username: String): WorkoutSession? {
            return getUserWorkoutSessions(context, username)
                .filter { it.workoutName == workoutName }
                .maxByOrNull { it.date }
        }

        private fun <T> saveToFile(context: Context, filename: String, data: List<T>): Boolean {
            return try {
                val fileOutputStream = context.openFileOutput(filename, Context.MODE_PRIVATE)
                val jsonString = gson.toJson(data)
                fileOutputStream.write(jsonString.toByteArray())
                fileOutputStream.close()
                Log.d(TAG, "File $filename saved successfully, data: $jsonString")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving file $filename: ${e.message}")
                e.printStackTrace()
                false
            }
        }

        private inline fun <reified T> loadFromFile(context: Context, filename: String): List<T>? {
            return try {
                val fileInputStream = context.openFileInput(filename)
                val inputString = fileInputStream.bufferedReader().use { it.readText() }
                fileInputStream.close()
                val type = object : TypeToken<List<T>>() {}.type
                val result = gson.fromJson<List<T>>(inputString, type)
                Log.d(TAG, "File $filename loaded successfully, data: $inputString")
                result
            } catch (e: FileNotFoundException) {
                Log.d(TAG, "File $filename not found, returning empty list")
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading file $filename: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
}