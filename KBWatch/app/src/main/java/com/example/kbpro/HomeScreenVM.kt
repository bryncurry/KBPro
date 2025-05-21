package com.example.kbpro

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class HomeScreenVM : ViewModel() {
    val workoutConfig: StateFlow<WorkoutConfig> = CurrentWorkoutModel.config

    private val _setsCompleted = mutableStateOf(0)
    val setsCompleted: State<Int> = _setsCompleted
} 