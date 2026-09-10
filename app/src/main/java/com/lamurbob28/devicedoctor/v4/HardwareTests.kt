package com.lamurbob28.devicedoctor.v4

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HardwareTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val kind = intent.getStringExtra("test") ?: "Touchscreen"
        setContent { DoctorTheme { HardwareScreen(kind, onClose = { finish() }) } }
    }
}

@Composable
private fun HardwareScreen(kind: String, onClose: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(kind, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = onClose, modifier = Modifier.testTag("close-hardware")) { Icon(Icons.Default.Close, "Close hardware test") }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).padding(20.dp)) {
            when (kind) {
                "Touchscreen" -> TouchscreenTest()
                "Display" -> DisplayTest()
                "Sound & vibration" -> SoundTest()
                else -> LiveSensorTest()
            }
        }
    }
}

@Composable
private fun TouchscreenTest() {
    var touched by remember { mutableStateOf(emptySet<Int>()) }
    var fingers by remember { mutableIntStateOf(0) }
    var maximumFingers by remember { mutableIntStateOf(0) }
    var resetKey by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Drag across the grid. Try two or more fingers. Unfilled cells may simply be untouched; they aren't an automatic fault diagnosis.", style = MaterialTheme.typography.bodyMedium)
        Text("${touched.size}/60 cells · Fingers: $fingers · Peak: $maximumFingers", modifier = Modifier.testTag("touch-count"))
        Canvas(Modifier.fillMaxWidth().weight(1f).testTag("touch-grid").semantics {
            contentDescription = "Touch test grid, ${touched.size} of 60 cells touched. Drag fingers across it."
        }.pointerInput(resetKey) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    fingers = pressed.size
                    maximumFingers = maxOf(maximumFingers, fingers)
                    if (size.width > 0 && size.height > 0) {
                        val cells = pressed.map { pointer ->
                            val col = (pointer.position.x / size.width * 6).toInt().coerceIn(0, 5)
                            val row = (pointer.position.y / size.height * 10).toInt().coerceIn(0, 9)
                            row * 6 + col
                        }
                        touched = touched + cells
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }) {
            val width = size.width / 6
            val height = size.height / 10
            for (row in 0..9) for (col in 0..5) {
                drawRect(if (row * 6 + col in touched) Color(0xFF80E6C4) else Color(0xFF29404D),
                    topLeft = Offset(col * width + 2.dp.toPx(), row * height + 2.dp.toPx()),
                    size = Size((width - 4.dp.toPx()).coerceAtLeast(0f), (height - 4.dp.toPx()).coerceAtLeast(0f)))
            }
        }
        OutlinedButton(onClick = { touched = emptySet(); fingers = 0; maximumFingers = 0; resetKey++ }, modifier = Modifier.fillMaxWidth().testTag("reset-grid")) { Text("Reset grid") }
    }
}

@Composable
private fun DisplayTest() {
    val colors = listOf("Red" to Color.Red, "Green" to Color.Green, "Blue" to Color.Blue, "White" to Color.White, "Black" to Color.Black)
    var index by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Inspect the solid area for unusual dots or discoloration. Move between colors at your own pace. Brightness stays under your control.", style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.fillMaxWidth().weight(1f).background(colors[index].second).testTag("display-color").semantics { contentDescription = "${colors[index].first} display test area" })
        Text("${index + 1}/5 · ${colors[index].first}")
        Button(onClick = { index = (index + 1) % colors.size }, modifier = Modifier.fillMaxWidth()) { Text("Next color") }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun SoundTest() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var tone by remember { mutableStateOf<ToneGenerator?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    fun stopTone() {
        tone?.let { runCatching { it.stopTone() }; runCatching { it.release() } }
        tone = null
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) { stopTone(); vibrator?.cancel() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stopTone(); vibrator?.cancel() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PanelCard {
            SectionTitle("Speaker / audio output")
            Text("Lower media volume first. This plays one short tone through your current audio output, which may be headphones or Bluetooth. You judge whether it sounds right.")
            Button(enabled = tone == null, onClick = {
                try {
                    val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 35)
                    tone = generator
                    if (!generator.startTone(ToneGenerator.TONE_PROP_BEEP, 500)) { stopTone(); notice = "The audio output could not start." }
                    else scope.launch { delay(650); if (tone === generator) stopTone() }
                } catch (_: Exception) { stopTone(); notice = "The audio output is unavailable." }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (tone == null) "Play short tone" else "Playing…") }
        }
        PanelCard {
            SectionTitle("Vibration motor")
            Text(if (vibrator?.hasVibrator() == true) "Tap to request a brief vibration. Device settings may suppress it; no vibration does not automatically mean a broken motor." else "Android does not report a vibration motor on this device.")
            Button(enabled = vibrator?.hasVibrator() == true, onClick = {
                try {
                    if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                    else vibrator?.vibrate(200)
                } catch (_: Exception) { notice = "Vibration is unavailable." }
            }, modifier = Modifier.fillMaxWidth()) { Text("Vibrate briefly") }
        }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LiveSensorTest() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val types = remember { listOf(Sensor.TYPE_ACCELEROMETER to "Acceleration (m/s²)", Sensor.TYPE_GYROSCOPE to "Rotation (rad/s)", Sensor.TYPE_LIGHT to "Ambient light (lux)", Sensor.TYPE_PROXIMITY to "Proximity (cm)") }
    val sensors = remember { types.associate { (type, _) -> type to manager?.getDefaultSensor(type) } }
    var readings by remember { mutableStateOf(emptyMap<Int, String>()) }
    var active by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, manager) {
        val lastUpdate = mutableMapOf<Int, Long>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = SystemClock.elapsedRealtime()
                if (now - (lastUpdate[event.sensor.type] ?: 0L) < 150) return
                lastUpdate[event.sensor.type] = now
                readings = readings + (event.sensor.type to event.values.take(3).joinToString("  ·  ") { oneDecimal(it.toDouble()) })
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        fun start() {
            if (active) return
            active = true
            sensors.forEach { (type, sensor) ->
                if (sensor != null && runCatching { manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) == true }.getOrDefault(false).not()) {
                    readings = readings + (type to "Could not start this sensor")
                }
            }
        }
        fun stop() { manager?.unregisterListener(listener); active = false }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) start()
            if (event == Lifecycle.Event.ON_PAUSE) stop()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Move the phone gently or cover its light/proximity sensor and watch for a change. Some phones expose only a near/far proximity value. These readings pause when you leave this screen.")
        Text(if (active) "Listening to available sensors" else "Readings paused", color = MaterialTheme.colorScheme.primary)
        types.forEach { (type, label) ->
            PanelCard {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(if (sensors[type] == null) "Not available on this device" else readings[type] ?: "Waiting for a reading…", style = MaterialTheme.typography.titleLarge)
                sensors[type]?.let { Text(it.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
