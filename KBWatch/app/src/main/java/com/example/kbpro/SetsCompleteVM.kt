package com.example.kbpro

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.State

class SetsCompleteVM : ViewModel() {
    private val _setsCompleted = mutableStateOf(0)
    val setsCompleted: State<Int> = _setsCompleted

    fun setSets() {
        _setsCompleted.value += 1
    }

}