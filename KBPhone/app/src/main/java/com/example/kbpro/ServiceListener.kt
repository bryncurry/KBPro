package com.example.kbpro

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

class ConfigListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // This method is triggered when a DataItem changes
        // You can inspect paths, keys, values here
    }
}