/**
 *  An app for WearOS, which tracks kettlebell swings for the user and sends them a buzz
 *  to their wrist when the desired number of swings have been completed.
 */
package com.example.kbpro.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import com.example.kbpro.HomeScreenVM
import com.example.kbpro.CurrentWorkoutModel

class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null

    private lateinit var tflite: Interpreter
    private val windowSize = 5
    private val slidingWindow = ArrayList<FloatArray>()
    private var lastRepTime = 0L

    private val viewModel: HomeScreenVM by viewModels()

    private var sets: Int = 0
    private var reps: Int = 0
    private var repCount = 0
    private var setCount = 0
    private var timerMs: Long = 10000
    private var exerciseIsStarted = false
    private var isInRep = false

    var timerScreenActive by mutableStateOf(false)
    var exerciseActiveScreen by mutableStateOf(false)
    var time by mutableStateOf(0)
    private var text = ""

    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called")

        tflite = loadModelFromAssets()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setTheme(android.R.style.Theme_DeviceDefault)
        setContent { 
            val config by viewModel.workoutConfig.collectAsState()
            sets = config.sets
            reps = config.reps
            MainContent(viewModel)
        }
    }

    /**
     * Loads the model for the CNN from the assets folder where it is contained, so that
     * we have it ready to classify reps.
     *
     * @return: An Interpreter object (the object required for TensorFlow lite)
     */
    private fun loadModelFromAssets(): Interpreter {
        val buffer = assets.open("kettlebell_model.tflite").use { inputStream ->
            val modelBytes = inputStream.readBytes()
            ByteBuffer.allocateDirect(modelBytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(modelBytes)
                rewind()
            }
        }
        return Interpreter(buffer)
    }

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSensorChanged(event: SensorEvent) {
            if (!exerciseIsStarted) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            trackSet(abs(x), abs(y), abs(z))
        }
    }

    /**
     * Given the user's x,y, and z accelerometer data, determines if that point (and the window
     * around it)represents a rep.
     *
     * The model is trained to detect reps based on the absolute value of acceleration being closed
     * to zero (in the x and y axis) and simply associated patterns in the z axis. This method
     * ensures if more than one point would be classified as a "peak", that we only count one point
     * as determining that a rep occurred.
     *
     * The model needs a sliding window of the size it was trained on.
     *
     * If the user is at the end of their set, sends them haptic feedback to let them know.
     */
    private fun trackSet(x: Float, y: Float, z: Float) {
        val now = System.currentTimeMillis()

        slidingWindow.add(floatArrayOf(x, y, z))
        if (slidingWindow.size > windowSize) slidingWindow.removeAt(0)
        if (slidingWindow.size < windowSize) return

        val input = Array(1) { Array(windowSize) { FloatArray(3) } }
        for (i in 0 until windowSize) {
            input[0][i] = slidingWindow[i]
        }

        val output = Array(1) { FloatArray(1) }
        tflite.run(input, output)
        val prediction = output[0][0]

        Log.d("RepDebug", "Prediction=$prediction | isInRep=$isInRep")

        if (prediction > 0.9 && !isInRep) {
            isInRep = true
            lastRepTime = now

            if (repCount == reps - 1) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500, 255))
                setCount++
                Log.d("RepDebug", "At set number $setCount")
                if (setCount < sets) startBreakTimer()
                exerciseActiveScreen = false
            }
            repCount++

            Log.d("RepDebug", "Counted rep at $now")
        } else if (prediction < 0.5 && isInRep) {
            isInRep = false
        }
    }

    private fun startBreakTimer() {
        timerScreenActive = true
        exerciseActiveScreen = false
        object : CountDownTimer(timerMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                time = (millisUntilFinished / 1000).toInt()
            }

            override fun onFinish() {
                startExercise()
            }
        }.start()
    }

    private fun startExercise() {
        repCount = 0
        exerciseIsStarted = false
        timerScreenActive = true
        exerciseActiveScreen = true

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                time = (millisUntilFinished / 1000).toInt()
                Handler(Looper.getMainLooper()).post {
                    vibrator?.vibrate(VibrationEffect.createOneShot(500, 255))
                }
            }

            override fun onFinish() {
                time = 0
                exerciseIsStarted = true
                timerScreenActive = false
                exerciseActiveScreen = true
            }
        }.start()
    }

    @Composable
    fun MainContent(
        viewModel: HomeScreenVM
    ) {
        // The VM, which is listening for events from the phone that might change some of our
        // composables.
        val workoutConfig by viewModel.workoutConfig.collectAsState()

        val showRepsScreen = remember { mutableStateOf(false) }
        val showSetsScreen = remember { mutableStateOf(false) }

        when {
            showSetsScreen.value -> SetsSelectorScreen { newSets ->
                CurrentWorkoutModel.updateConfig(newSets, workoutConfig.reps)
                showSetsScreen.value = false
            }
            showRepsScreen.value -> RepsSelectorScreen { newReps ->
                CurrentWorkoutModel.updateConfig(workoutConfig.sets, newReps)
                showRepsScreen.value = false
            }
            timerScreenActive -> TimerScreen()
            exerciseActiveScreen -> ActiveExerciseScreen()
            else -> MainScreen(
                showRepsScreen,
                showSetsScreen,
                sets = workoutConfig.sets,
                reps = workoutConfig.reps,
                viewModel
            )
        }
    }

    @Composable
    fun TimerScreen() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("$time", fontSize = 30.sp)
        }
    }

    @Composable
    fun ActiveExerciseScreen() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DotsFlashing()
        }
    }

    @Composable
    fun MainScreen(
        showRepsScreen: MutableState<Boolean>,
        showSetsScreen: MutableState<Boolean>,
        sets: Int,
        reps: Int,
        viewModel: HomeScreenVM = viewModel()
    ) {
        val setsCompleted by viewModel.setsCompleted

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SetsProgressIndicator(currSetsComplete = setsCompleted, totalSets = sets)
//            Row(
//                horizontalArrangement = Arrangement.spacedBy(8.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Button(onClick = { showSetsScreen.value = true }) {
//                    Text(
//                        text = if (sets == 0) "Sets" else sets.toString(),
//                        fontSize = 18.sp
//                    )
//                }
//                Text("×", fontSize = 18.sp)
//                Button(onClick = { showRepsScreen.value = true }) {
//                    Text(
//                        text = if (reps == 0) "Reps" else reps.toString(),
//                        fontSize = 18.sp
//                    )
//                }
//            }
            Button(
                onClick = { 
                    startExercise()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("Start", fontSize = 18.sp)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }
}

@Composable
fun SetsSelectorScreen(onSetsSelected: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items((1..100).toList()) { num ->
            Button(onClick = { onSetsSelected(num) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(text = num.toString())
            }
        }
    }
}

@Composable
fun RepsSelectorScreen(onRepsSelected: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items((1..100).toList()) { num ->
            Button(onClick = { onRepsSelected(num) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(text = num.toString())
            }
        }
    }
}

@Composable
fun SetsProgressIndicator(currSetsComplete: Int, totalSets: Int){
    val currPercentage = currSetsComplete.toFloat() / totalSets.toFloat()
    val animatedProgress = animateFloatAsState(currPercentage).value
    Box(
        contentAlignment = Alignment.Center,
    ){
        Canvas(modifier = Modifier.size(100.dp)) {
            drawArc(
                color = Color.Green,
                startAngle = -90f,
                sweepAngle = 360 * animatedProgress,
                useCenter = false,
                style = Stroke(width = 12f)
            )
        }
        Text("$currSetsComplete/$totalSets")
    }
}


/**
 * This code is not mine and all credit goes to:
 * https://github.com/razaghimahdi/Android-Loading-Dots
 */
@Composable
fun DotsFlashing() {
    val minAlpha = 0.1f
    val dotSize = 24.dp
    val delayUnit = 300

    @Composable
    fun Dot(alpha: Float) = Spacer(
        Modifier
            .size(dotSize)
            .alpha(alpha)
            .background(
                color = MaterialTheme.colors.primary,
                shape = CircleShape
            )
    )

    val infiniteTransition = rememberInfiniteTransition()

    @Composable
    fun animateAlphaWithDelay(delay: Int) = infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = minAlpha,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = delayUnit * 4
                minAlpha at delay with LinearEasing
                1f at delay + delayUnit with LinearEasing
                minAlpha at delay + delayUnit * 2
            }
        )
    )

    val alpha1 by animateAlphaWithDelay(0)
    val alpha2 by animateAlphaWithDelay(delayUnit)
    val alpha3 by animateAlphaWithDelay(delayUnit * 2)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val spaceSize = 2.dp

        Dot(alpha1)
        Spacer(Modifier.width(spaceSize))
        Dot(alpha2)
        Spacer(Modifier.width(spaceSize))
        Dot(alpha3)
    }
} 