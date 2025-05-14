package com.example.kbpro

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList

class KBProViewModel : ViewModel() {
    //This emits whenever the data here changes!
    private val _repDataPoints = MutableStateFlow<List<RepDataPoint>>(emptyList())
    val repDataPoints = _repDataPoints.asStateFlow()

    fun addDataPoint(dataPoint: RepDataPoint) {
        _repDataPoints.value += dataPoint
    }

    fun getListSize() : Int {
        return _repDataPoints.value.size;
    }

    fun clearDataPoints() {
        Log.d("Bryn:", "List size before clear: ${_repDataPoints.value.size}")
        _repDataPoints.value = emptyList()
        Log.d("Bryn:", "Cleared list size: ${_repDataPoints.value.size}")
        Log.d("Bryn:", "Exposed list size: ${repDataPoints.value.size}")
    }
}