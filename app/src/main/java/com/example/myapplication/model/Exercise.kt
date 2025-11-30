package com.example.myapplication.model

import java.io.Serializable

data class Exercise(
    val name: String,
    val sets: Int,
    val previousSets: List<Set> = emptyList()
) : Serializable

data class Set(
    val weight: Double,
    val reps: Int
) : Serializable