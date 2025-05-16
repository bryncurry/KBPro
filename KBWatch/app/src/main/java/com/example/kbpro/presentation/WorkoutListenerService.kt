package com.example.kbpro.presentation

import android.content.Intent
import android.util.Log
import com.example.kbpro.WorkoutRepository
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class WorkoutListenerService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path

                if (path == "/workout_config") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val sets = dataMap.getInt("sets")
                    val reps = dataMap.getInt("reps")
                    
                    WorkoutRepository.updateConfig(sets, reps)

                }
            }
        }
    }
} 