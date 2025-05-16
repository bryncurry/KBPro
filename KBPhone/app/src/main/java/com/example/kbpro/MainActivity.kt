package com.example.kbpro

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.kbpro.ui.theme.KBProTheme
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

/**
 * A class that is the equivalent of a "tuple" - an instance of this class stores an x value,
 * and whether that particular x value was classified as a new rep.
 */
data class RepDataPoint(val x: Float, val isRep: Boolean)

class MainActivity : ComponentActivity() {
    private val viewModel: KBProViewModel by viewModels<KBProViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KBProTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SendWorkoutScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun SendWorkoutScreen() {
    val context = LocalContext.current
    val sets = remember { mutableStateOf(3) }
    val reps = remember { mutableStateOf(10) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Sets: ${sets.value}")
        Button(onClick = { sets.value++ }) { Text("Increase Sets") }
        Button(onClick = { sets.value = (sets.value - 1).coerceAtLeast(1) }) { Text("Decrease Sets") }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Reps: ${reps.value}")
        Button(onClick = { reps.value++ }) { Text("Increase Reps") }
        Button(onClick = { reps.value = (reps.value - 1).coerceAtLeast(1) }) { Text("Decrease Reps") }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            sendWorkoutData(context, sets.value, reps.value)
        }) {
            Text("Send to Watch")
        }
    }
}

@Composable
fun LineChartCompose(viewModel: KBProViewModel) {
    val repDataPoints by viewModel.repDataPoints.collectAsState()

    Log.d("LineChartCompose", "Received ${repDataPoints.size} data points")

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
            }
        },
        update = { lineChart ->
            lineChart.clear()

            Log.d("LineChartCompose", "Updating LineChart with ${repDataPoints.size} data points")

            // Separate the data points into normal and rep entries
            val normalEntries = repDataPoints.mapIndexedNotNull { index, point ->
                if (!point.isRep) Entry(index.toFloat(), point.x) else null
            }
            val repEntries = repDataPoints.mapIndexedNotNull { index, point ->
                if (point.isRep) Entry(index.toFloat(), point.x) else null
            }

            Log.d("LineChartCompose", "Created ${normalEntries.size} normal entries")
            Log.d("LineChartCompose", "Created ${repEntries.size} rep entries")

            // Normal data points
            val normalDataSet = LineDataSet(normalEntries, "Normal Data").apply {
                color = Color.BLUE
                valueTextColor = Color.BLACK
                setDrawValues(false)
                setDrawCircles(true)
                circleRadius = 4f
                setCircleColor(Color.BLUE)
                circleHoleColor = Color.BLUE
            }

            // Rep data points
            val repDataSet = LineDataSet(repEntries, "Rep Data").apply {
                color = Color.RED
                valueTextColor = Color.BLACK
                setDrawValues(false)
                setDrawCircles(true)
                circleRadius = 6f
                setCircleColor(Color.RED)
                circleHoleColor = Color.WHITE
            }

            // Combine both data sets
            val lineData = LineData(normalDataSet, repDataSet)
            lineChart.data = lineData
            lineChart.invalidate() // refresh the chart
        },
        modifier = Modifier.fillMaxSize()
    )
}

fun sendWorkoutData(context: Context, sets: Int, reps: Int) {
    val TAG = "SendWorkoutData"
    Log.d(TAG, "Preparing to send workout data: sets=$sets, reps=$reps")
    
    val dataClient = Wearable.getDataClient(context)
    val path = "/workout_config"
    
    val dataMap = com.google.android.gms.wearable.PutDataMapRequest.create(path).apply {
        dataMap.putInt("sets", sets)
        dataMap.putInt("reps", reps)
        dataMap.putLong("timestamp", System.currentTimeMillis())
        Log.d(TAG, "Created DataMap with path=$path")
    }

    val request = dataMap.asPutDataRequest().setUrgent()
    Log.d(TAG, "Sending DataItem request...")

    dataClient.putDataItem(request)
        .addOnSuccessListener {
            Log.d(TAG, "Successfully sent workout data: sets=$sets, reps=$reps")
        }
        .addOnFailureListener { e ->
            Log.e(TAG, "Failed to send workout data: sets=$sets, reps=$reps", e)
        }
}





