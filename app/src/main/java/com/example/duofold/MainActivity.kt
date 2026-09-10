package com.example.duofold

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.duofold.sensors.FoldMotionModel
import com.example.duofold.ui.ContentView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fullscreen edge-to-edge: transparent status + nav bars with the UI
        // drawing behind them. Light style = dark icons, matching our light UI.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val context = LocalContext.current
                val motionModel = remember { FoldMotionModel(context.applicationContext) }
                // Wire sensor lifecycle to the composition (mirrors the Activity
                // lifecycle closely enough for a fullscreen demo effect).
                DisposableEffect(motionModel) {
                    motionModel.start()
                    onDispose { motionModel.stop() }
                }
                ContentView(motionModel = motionModel)
            }
        }
    }
}
