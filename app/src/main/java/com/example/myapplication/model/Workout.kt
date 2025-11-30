package com.example.myapplication.model

import java.io.Serializable

data class Workout(
    val name: String,
    val exercises: List<Exercise>,
    val createdBy: String
) : Serializable