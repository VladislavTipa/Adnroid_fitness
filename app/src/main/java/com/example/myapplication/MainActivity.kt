package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.auth.LoginActivity
import com.example.myapplication.workout.CreateWorkoutActivity
import com.example.myapplication.workout.WorkoutHistoryActivity

class MainActivity : AppCompatActivity() {

    private lateinit var welcomeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        welcomeText = findViewById(R.id.welcomeText)
        val createWorkoutCard = findViewById<android.widget.LinearLayout>(R.id.createWorkoutCard)
        val historyCard = findViewById<android.widget.LinearLayout>(R.id.historyCard)
        val logoutCard = findViewById<android.widget.LinearLayout>(R.id.logoutCard)

        val username = intent.getStringExtra("username") ?: "Пользователь"
        welcomeText.text = "Привет, $username! 👋"

        createWorkoutCard.setOnClickListener {
            val intent = Intent(this, CreateWorkoutActivity::class.java)
            intent.putExtra("username", username)
            startActivity(intent)
        }

        historyCard.setOnClickListener {
            val intent = Intent(this, WorkoutHistoryActivity::class.java)
            intent.putExtra("username", username)
            startActivity(intent)
        }

        logoutCard.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}