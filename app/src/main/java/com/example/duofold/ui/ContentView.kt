package com.example.duofold.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.duofold.sensors.FoldMotionModel

/**
 * Composition root: [DemoContentView] wrapped in [foldEffect], with tilt
 * controls (mode switch, manual slider, recalibration) tucked behind the
 * Settings button instead of a permanent floating panel.
 */
@Composable
fun ContentView(motionModel: FoldMotionModel) {
    val sensorTilt by motionModel.tiltDegrees.collectAsState()
    val sensorHinge by motionModel.hingeSide.collectAsState()
    val hasSensor by motionModel.hasSensor.collectAsState()

    var manualTilt by remember { mutableFloatStateOf(0f) }
    // Default to manual control when no rotation-vector sensor exists.
    var useSensor by remember(hasSensor) { mutableStateOf(hasSensor) }
    var showSettings by remember { mutableStateOf(false) }
    var autoRecenter by remember { mutableStateOf(true) }
    val effectiveUseSensor = useSensor && hasSensor

    val tilt = if (effectiveUseSensor) sensorTilt else manualTilt
    val hinge: Float = if (effectiveUseSensor) {
        sensorHinge
    } else {
        if (manualTilt >= 0f) 1f else -1f
    }

    val params = remember { FoldParameters() }

    Box(Modifier.fillMaxSize()) {
        // Folded content. On pre-33 (previews only — minSdk is 33) show it plain.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            DemoContentView(
                Modifier.foldEffect(
                    tiltDegrees = tilt,
                    hingeSide = hinge,
                    parameters = params
                ),
                onCalibrateClick = { motionModel.recalibrate() },
                onSettingsClick = { showSettings = true }
            )
        } else {
            DemoContentView(
                Modifier,
                onCalibrateClick = { motionModel.recalibrate() },
                onSettingsClick = { showSettings = true }
            )
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Settings") },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "Tilt: %.1f°  ·  hinge %s".format(
                                tilt,
                                if (hinge < 0f) "L" else "R"
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (!hasSensor) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "No rotation sensor — manual mode",
                                color = Color(0xFFFDB022),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Slider(
                            value = if (effectiveUseSensor) sensorTilt else manualTilt,
                            onValueChange = {
                                if (effectiveUseSensor) {
                                    // Nudge path: dragging in sensor mode drops
                                    // to manual so the shader stays debuggable.
                                    useSensor = false
                                    manualTilt = it
                                } else {
                                    manualTilt = it
                                }
                            },
                            valueRange = -45f..45f
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (effectiveUseSensor) "Sensor" else "Manual",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = effectiveUseSensor,
                                onCheckedChange = { useSensor = it },
                                enabled = hasSensor
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Auto-calibrate",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = autoRecenter,
                                onCheckedChange = {
                                    autoRecenter = it
                                    motionModel.setAutoRecenterEnabled(it)
                                }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                motionModel.recalibrate()
                                manualTilt = 0f
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Recalibrate")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}
