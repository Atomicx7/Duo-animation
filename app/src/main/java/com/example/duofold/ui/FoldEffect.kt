package com.example.duofold.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.duofold.R
import java.io.BufferedReader

/** In-memory fallback so previews/tests without res/raw still work. */
private const val FALLBACK_PX_PER_MM = 6f

/**
 * Reads res/raw/duo_fold.agsl into a String. Called once per composition
 * (remembered by the caller) so per-frame cost is just uniform uploads.
 */
private fun loadShaderSource(context: android.content.Context): String {
    return try {
        context.resources.openRawResource(R.raw.duo_fold).use { stream ->
            stream.bufferedReader().use(BufferedReader::readText)
        }
    } catch (_: Exception) {
        // Should never happen in a real build; keeps @Preview from crashing.
        ""
    }
}

/**
 * Applies the Duo-Fold frosted-glass effect to this layout subtree.
 *
 * Wraps [Modifier.graphicsLayer] + AGSL [RuntimeShader], the direct analog of
 * SwiftUI's `.layerEffect` (+ `.compositingGroup` flattening, which
 * `graphicsLayer` capture gives us). Everything under this modifier must be
 * Compose-native (Text, Image, Canvas, …) so it rasterizes cleanly into the
 * layer texture captured as the shader's `content` input.
 *
 * @param tiltDegrees Signed tilt in degrees around the screen-space Y axis.
 *   Positive = right edge farther from the viewer (hinge right, frost on the
 *   left), negative = hinge left — mirrors `DuoFold.metal`'s `angle` convention.
 * @param hingeSide +1f = hinge on right edge, -1f = hinge on left edge.
 *   Resolved at runtime from the tilt sign — never hardcoded.
 * @param parameters Physical tuning (eye distance, blur/darken curves).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Modifier.foldEffect(
    tiltDegrees: Float,
    hingeSide: Float = if (tiltDegrees >= 0f) 1f else -1f,
    parameters: FoldParameters = FoldParameters()
): Modifier = composed {
    // Min SDK is 33, so RuntimeShader/RenderEffect always exist. The guard is
    // only here for IDE preview clarity.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this

    val context = LocalContext.current
    val density = LocalDensity.current
    val displayMetrics: DisplayMetrics = context.resources.displayMetrics

    // Shader source is a raw asset string (res/raw/duo_fold.agsl).
    val shaderSrc = androidx.compose.runtime.remember {
        loadShaderSource(context)
    }
    // Auto-resolve px/mm from the physical display when the caller didn't pass one.
    // xdpi = pixels per inch -> /25.4 = pixels per mm.
    val autoPxPerMm = androidx.compose.runtime.remember(displayMetrics) {
        val xdpi = displayMetrics.xdpi
        if (xdpi.isFinite() && xdpi > 0f) xdpi / 25.4f else FALLBACK_PX_PER_MM
    }
    val resolvedPxPerMm = if (parameters.pixelsPerMillimeter > 0f) {
        parameters.pixelsPerMillimeter
    } else {
        autoPxPerMm
    }

    // RuntimeShader instances are not thread-safe for concurrent mutation, so
    // build a fresh one per graphicsLayer application. The AGSL compile cost
    // is small relative to one full-screen layer, and correctness (uniforms
    // matching the current size/tilt) matters more here.
    this.graphicsLayer {
        if (shaderSrc.isEmpty() || size.width <= 1f || size.height <= 1f) {
            renderEffect = null
            return@graphicsLayer
        }
        val shader = try {
            RuntimeShader(shaderSrc).apply {
                setFloatUniform("resolution", size.width, size.height)
                setFloatUniform("tiltDegrees", tiltDegrees)
                setFloatUniform(
                    "eyeDistancePx",
                    parameters.eyeDistanceMillimeters * resolvedPxPerMm
                )
                setFloatUniform("hingeSide", hingeSide)
                setFloatUniform("blurSpread", parameters.blurSpread)
                // `darkening` is authored per point at the 6 units/mm reference
                // density of the original (see FoldParameters). Radius is measured
                // in physical device px, so normalize — otherwise dense screens
                // darken ~pxPerMm/6x too fast and crush to black (iOS-unfaithful).
                setFloatUniform("darkening", parameters.darkening * 6f / resolvedPxPerMm)
            }
        } catch (e: Exception) {
            Log.e("FoldEffect", "RuntimeShader failed: ${e.message}", e)
            renderEffect = null
            return@graphicsLayer
        }
        renderEffect = try {
            RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        } catch (e: Exception) {
            Log.e("FoldEffect", "createRuntimeShaderEffect failed: ${e.message}", e)
            null
        }
        clip = true
        // Silence unused-density warning: density is intentionally captured so
        // recomposition follows display-metric changes on foldables.
        @Suppress("UNUSED_EXPRESSION")
        density
    }
}
