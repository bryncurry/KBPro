package com.example.kbpro

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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

class MainActivity : ComponentActivity(){

    private val viewModel: KBProViewModel by viewModels<KBProViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KBProTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LineChartCompose(viewModel)
                }
            }
        }

    }


    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KBProTheme {
        Greeting("Android")
    }
}

/**
 * MPAndroidChart, which I'm using to create the charts, is not made for JPC, but for traditional
 * views. This uses Android View, which allows wrapping up a traditional view into a composable.
 */

@Composable
fun LineChartCompose(viewModel: KBProViewModel) {
    val repDataPoints by viewModel.repDataPoints.collectAsState()

    Log.d("LineChartCompose", "Received ${repDataPoints.size} data points")

    /**
     * AndroidView allows us to use Android Views in JPC. Since the chart stuff was designed for
     *  views, we need this. The factory lambda only makes this view once. Notably, that means we
     *  need update. From Android docs:
     *
     *  AndroidView also provides an update callback that is called when the view is inflated.
     *  The AndroidView recomposes whenever a State read within the callback changes.
     *
     *  This allows us to recompose.
     */
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





