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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * An activity which allows the user to put in a desired amount of sets and reps to perform.
 * When the user clicks the start button, displays a timer for them to get into place. Sends
 * a buzz to their wrist when their set is complete, then gives them a cooldown timer until the
 * next set begins.
 */
class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null

    // https://medium.com/walmartglobaltech/custom-tensorflow-lite-model-implementation-in-android-5c1c65bd9f97
    // Interpreter class loads the model and runs inference.
    private lateinit var tflite: Interpreter
    // How many points to feed to the model at once -> Must match trained model's window size.
    private val windowSize = 5
    private val slidingWindow = ArrayList<FloatArray>()
    private var lastRepTime = 0L

    // How many sets and reps the user is trying to complete, and how many they currently have done.
    private var sets: Int = 0
    private var reps: Int = 0
    private var repCount = 0
    private var setCount = 0
    // 60,000 is one minute
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

        // Get the model ready to use.
        tflite = loadModelFromAssets()

        // Register the vibrator.
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Get the accelerometer, so that we can get readings as the user performs reps.
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setTheme(android.R.style.Theme_DeviceDefault)
        setContent { MainContent() }
    }

    /**
     * Using the model in the assets folder, prepares the model to receive data.
     */
    private fun loadModelFromAssets(): Interpreter {
        // readBytes creates a stream that must be closed by the caller, use will automatically
        // close it down.
        val buffer = assets.open("kettlebell_model.tflite").use { inputStream ->
            val modelBytes = inputStream.readBytes()
            ByteBuffer.allocateDirect(modelBytes.size).apply {
                order(ByteOrder.nativeOrder())
                // Actually puts the bytes in the buffer.
                put(modelBytes)
                // Ensures we are at the beginning of the buffer instead of the end.
                rewind()
            }
        }
        return Interpreter(buffer)
    }

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        // Called when we get a new sensor event from the accelerometer.
        override fun onSensorChanged(event: SensorEvent) {
            if (!exerciseIsStarted) return

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            trackSet(abs(x), abs(y), abs(z))
        }
    }

    /**
     * Given the current x,y,z of the most recent sensor reading, puts it through the model, if
     * the window is large enough.
     *
     * Based on the model's prediction (whether or not it thinks a rep occurred), adds to the set,
     * or gets ready to listen for more reps.
     */
    private fun trackSet(x: Float, y: Float, z: Float) {
        val now = System.currentTimeMillis()

        slidingWindow.add(floatArrayOf(x, y, z))
        // If we're larger than the set window size, remove the oldest result as it's been fully
        // examined.
        if (slidingWindow.size > windowSize) slidingWindow.removeAt(0)
        // If we don't have a full window, we can't run the model.
        if (slidingWindow.size < windowSize) return

        // Array(1){ ... } makes an array with one element and the inside is the element,
        // which is an array of size 5 (window size), where each index has an array of 3.
        // So, a 1x5x3 array.
        val input = Array(1) { Array(windowSize) { FloatArray(3) } }
        for (i in 0 until windowSize) {
            input[0][i] = slidingWindow[i]
        }

        val output = Array(1) { FloatArray(1) }
        tflite.run(input, output)
        // If high, probably a rep, if low, probably not one of the accelerometer peaks that mark
        // a rep.
        val prediction = output[0][0]

        Log.d("RepDebug", "Prediction=$prediction | isInRep=$isInRep")

        // If we were previously in a zone not considered to be a rep, but the prediction indicates
        // we're likely in an accelerometer peak, mark this as a rep. Do not count further high
        // predictions as a rep, because they are likely in the same rep. Instead, reset when the
        // prediction is low again.
        if (prediction > 0.9 && !isInRep) {
            isInRep = true
            lastRepTime = now

            // Count rep
            if (repCount == reps - 1) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500, 255))
                setCount++
                Log.d("RepDebug", "At set number $setCount")
                if (setCount < sets) startBreakTimer()
                // Reset this in case there was only one set, in which case the break timer won't
                // reset it for us.
                exerciseActiveScreen = false
            }
            repCount++

            Log.d("RepDebug", "Counted rep at $now")
        } else if (prediction < 0.5 && isInRep) {
            // Reset the flag once prediction has dropped again
            isInRep = false
        }
    }

    /**
     * Begins a break, for a duration set by the user.
     */
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

    /**
     * Begins one set of exercise (meaning, one group of reps). Starts by resetting the rep count,
     * so that reps from previous sets are not counted. Sends the user a message to get in position
     * to start, with buzzes accompanying it to act as a countdown, letting them know when they
     * can start.
     */
    private fun startExercise() {
        // Reset state of set and view
        repCount = 0
        exerciseIsStarted = false
        timerScreenActive = true
        exerciseActiveScreen = true

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                time = (millisUntilFinished / 1000).toInt()
                // Just firing off the vibrator causes a delay in the display of the timer, so
                // need it to not hog thread.
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
    fun MainContent() {
        val showRepsScreen = remember { mutableStateOf(false) }
        val showSetsScreen = remember { mutableStateOf(false) }

        when {
            showSetsScreen.value -> SetsSelectorScreen { sets = it; showSetsScreen.value = false }
            showRepsScreen.value -> RepsSelectorScreen { reps = it; showRepsScreen.value = false }
            timerScreenActive -> TimerScreen()
            exerciseActiveScreen -> ActiveExerciseScreen()
            else -> MainScreen(showRepsScreen, showSetsScreen)
        }
    }

    /**
     * Displays the time left in a given timer to the user.
     */
    @Composable
    fun TimerScreen() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("$time", fontSize = 30.sp)
        }
    }

    /**
     * Shows dots on the screen to show the screen is still active while the user is exercising.
     */
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

    /**
     * The main screen has two buttons, sets and reps, and a start button. Selecting sets and
     * reps will allow the user to customize their workout numbers, start will begin the workout.
     */
    @Composable
    fun MainScreen(showRepsScreen: MutableState<Boolean>, showSetsScreen: MutableState<Boolean>) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Button(onClick = { showSetsScreen.value = true }) {
                    Text(if (sets == 0) "Sets" else sets.toString())
                }
                Text("x")
                Button(onClick = { showRepsScreen.value = true }) {
                    Text(if (reps == 0) "Reps" else reps.toString())
                }
            }
            Button(onClick = { startExercise() }, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text("Start")
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

/**
 * All credit goes to: https://gist.github.com/EugeneTheDev/a27664cb7e7899f964348b05883cbccd
 */
@Composable
fun DotsFlashing() {
    val minAlpha = 0.1f
    val dotSize = 24.dp
    val delayUnit = 300

    @Composable
    fun Dot(
        alpha: Float
    ) = Spacer(
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



