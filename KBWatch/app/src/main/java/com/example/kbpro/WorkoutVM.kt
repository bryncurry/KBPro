package com.example.kbpro

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class WorkoutVM : ViewModel() {
    val workoutConfig: StateFlow<WorkoutConfig> = WorkoutRepository.config
} 