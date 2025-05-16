package com.example.kbpro

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkoutConfig(
    val sets: Int = 0,
    val reps: Int = 0
)

object WorkoutRepository {
    private val _config = MutableStateFlow(WorkoutConfig())
    val config: StateFlow<WorkoutConfig> = _config.asStateFlow()


    fun updateConfig(sets: Int, reps: Int) {
        val newConfig = WorkoutConfig(sets, reps)
        _config.value = newConfig
    }

    fun getCurrentConfig(): WorkoutConfig {
        return _config.value
    }
} 