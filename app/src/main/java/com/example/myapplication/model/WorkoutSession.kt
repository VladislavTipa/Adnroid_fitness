package com.example.myapplication.model

import java.util.Date
import java.io.Serializable

data class WorkoutSession(
    val workoutName: String,
    val exercises: List<Exercise>,
    val date: Date,
    val duration: Long,
    val username: String
) : Serializable