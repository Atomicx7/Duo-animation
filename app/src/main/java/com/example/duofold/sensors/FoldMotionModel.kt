package com.example.duofold.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Sensor model mirroring FoldMotionModel.swift.
 *
 * Derives the device's tilt around the screen-space Y axis, relative to a
 * calibrated "zero tilt" pose (the first sample; [recalibrate] re-latches it).
 *
 * - Rotation source is the game rotation vector (gyro + accel, **no
 *   magnetometer**) — the Android analog of Core Motion's
 *   `.xArbitraryZVertical`. The original deliberately avoids mag correction:
 *   yaw is exactly the axis this effect tracks, and mag would trade latency
 *   for long-term stability we don't need. Falls back to the fused rotation
 *   vector only on devices without a game-rotation sensor. There is no compass
 *   dependence: tilting/turning the phone moves the effect, changing magnetic
 *   heading alone does not re-zero anything.
 * - Per sample: remap the rotation matrix into screen axes (columns =
 *   screen-right, screen-up, screen-normal, honoring display rotation, same
 *   role as the Swift `screenAxesInDeviceSpace`), then
 *   `relative = referenceᵀ · current` and
 *   `measured = atan2(normal.x, normal.z)` — the screen normal's excursion
 *   toward screen-right. Positive = right edge farther from the viewer.
 * - Gyro prediction along the screen's Y axis covers sensor/display latency,
 *   then a light low-pass (`SMOOTHING`) keeps hand and screen glued.
 * - Auto-recenter washout: a slow baseline (`RECENTER_TAU_S`) continuously
 *   absorbs gyro drift and slow posture shifts — but only while the device is
 *   nearly still — so the zero pose maintains itself without hammering
 *   Calibrate. Toggle with [setAutoRecenterEnabled]; Calibrate still snaps
 *   instantly at any time.
 * - Hinge follows the tilt sign: positive tilt → hinge right (+1, frost on
 *   the left), negative tilt → hinge left (-1). Never hardcoded to one side.
 * - Exposes [tiltDegrees], [hingeSide] and [hasSensor] as StateFlows. With no
 *   rotation sensor at all, [hasSensor] is false and the UI must fall back to
 *   the manual slider.
 */
class FoldMotionModel(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val rawMatrix = FloatArray(9)
    private val screenMatrix = FloatArray(9)
    private var reference: FloatArray? = null
    private var pendingRecalibrate = false

    private val gyroRate = FloatArray(3)
    private var hasGyroSample = false

    /** Smoothed tilt in radians (mirrors Swift `motionTilt`). */
    private var tiltRad = 0f
    /** Slow baseline for auto-recenter (same units/frame as [tiltRad]). */
    private var baselineRad = 0f
    private var lastPredicted = 0f
    private var autoRecenter = true
    private var lastTimestampNs = 0L

    private val _tiltDegrees = MutableStateFlow(0f)
    val tiltDegrees: StateFlow<Float> = _tiltDegrees.asStateFlow()

    private val _hingeSide = MutableStateFlow(1f)
    val hingeSide: StateFlow<Float> = _hingeSide.asStateFlow()

    private val _hasSensor = MutableStateFlow(rotationSensor != null)
    val hasSensor: StateFlow<Boolean> = _hasSensor.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (rotationSensor == null) {
            _hasSensor.value = false
        }
    }

    fun stop() {
        if (!started) return
        started = false
        sensorManager.unregisterListener(this)
    }

    /** Makes the current pose the zero-tilt pose (mirrors Swift `recalibrate`). */
    fun recalibrate() {
        pendingRecalibrate = true
        // Snap back immediately while waiting for the next sample.
        tiltRad = 0f
        baselineRad = 0f
        _tiltDegrees.value = 0f
    }

    /**
     * Enables/disables the auto-recenter washout. Enabling snaps the baseline
     * to the latest reading so output doesn't jump.
     */
    fun setAutoRecenterEnabled(enabled: Boolean) {
        autoRecenter = enabled
        if (enabled) {
            baselineRad = lastPredicted
        }
    }

    /**
     * Manual override path for emulators / fine control. Clamped to ±45° to
     * match the shader's stable range.
     */
    fun setManualTilt(degrees: Float) {
        val clamped = degrees.coerceIn(-MAX_TILT, MAX_TILT)
        _tiltDegrees.value = clamped
        _hingeSide.value = if (clamped >= 0f) 1f else -1f
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rawMatrix, event.values)
                remapToScreenAxes(rawMatrix, screenMatrix)

                if (reference == null || pendingRecalibrate) {
                    reference = screenMatrix.copyOf()
                    pendingRecalibrate = false
                    tiltRad = 0f
                    _tiltDegrees.value = 0f
                    return
                }

                // Current screen axes expressed in the calibrated screen frame.
                val rel = multiply3(transpose3(reference!!), screenMatrix)
                // Current screen normal (column 2): excursion toward screen-right.
                val measured = atan2(rel[2].toDouble(), rel[8].toDouble()).toFloat()

                // Extrapolate along the rotation rate around the screen's Y axis.
                var predicted = measured
                if (hasGyroSample) {
                    val sy = screenYinDeviceCoords()
                    val omegaY =
                        gyroRate[0] * sy[0] + gyroRate[1] * sy[1] + gyroRate[2] * sy[2]
                    predicted = measured + omegaY * PREDICTION_INTERVAL
                }
                lastPredicted = predicted

                // Auto-recenter washout: while the device is nearly still, drag
                // the slow baseline toward the reading so drift can't accumulate
                // into a permanent offset. Deliberate motion is untouched.
                val nowNs = event.timestamp
                val dtS = if (lastTimestampNs == 0L) 0.02f
                else ((nowNs - lastTimestampNs) / 1_000_000_000f).coerceIn(0f, 0.5f)
                lastTimestampNs = nowNs
                if (autoRecenter) {
                    val omegaMag = if (hasGyroSample) {
                        sqrt(
                            gyroRate[0] * gyroRate[0] +
                                gyroRate[1] * gyroRate[1] +
                                gyroRate[2] * gyroRate[2]
                        )
                    } else {
                        0f
                    }
                    if (omegaMag < STILL_THRESHOLD_RAD_S) {
                        val alpha = (dtS / RECENTER_TAU_S).coerceIn(0f, 1f)
                        baselineRad += wrapAngle(predicted - baselineRad) * alpha
                    }
                }
                val target =
                    if (autoRecenter) wrapAngle(predicted - baselineRad) else predicted

                tiltRad += wrapAngle(target - tiltRad) * SMOOTHING
                val tiltDeg = Math.toDegrees(tiltRad.toDouble()).toFloat()
                    .coerceIn(-MAX_TILT, MAX_TILT)
                _tiltDegrees.value = tiltDeg
                _hingeSide.value = if (tiltDeg >= 0f) 1f else -1f
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroRate[0] = event.values[0]
                gyroRate[1] = event.values[1]
                gyroRate[2] = event.values[2]
                hasGyroSample = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Remaps a device-frame rotation matrix into screen axes (columns =
     * screen-right, screen-up, screen-normal), honoring display rotation.
     * Same role as Swift's `screenAxesInDeviceSpace`.
     */
    private fun remapToScreenAxes(raw: FloatArray, out: FloatArray) {
        val (axisX, axisY) = screenRemapAxes()
        SensorManager.remapCoordinateSystem(raw, axisX, axisY, out)
    }

    private fun displayRotation(): Int =
        try {
            appContext.display?.rotation ?: Surface.ROTATION_0
        } catch (_: Exception) {
            Surface.ROTATION_0
        }

    private fun screenRemapAxes(): Pair<Int, Int> = when (displayRotation()) {
        Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
        Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
    }

    /** Screen-up (Y) axis expressed in raw device coordinates (for gyro dot). */
    private fun screenYinDeviceCoords(): FloatArray = when (displayRotation()) {
        Surface.ROTATION_90 -> floatArrayOf(-1f, 0f, 0f)
        Surface.ROTATION_180 -> floatArrayOf(0f, -1f, 0f)
        Surface.ROTATION_270 -> floatArrayOf(1f, 0f, 0f)
        else -> floatArrayOf(0f, 1f, 0f)
    }

    /** Wraps an angle to [-PI, PI]. */
    private fun wrapAngle(a: Float): Float {
        var x = a % (2 * Math.PI.toFloat())
        if (x > Math.PI) x -= (2 * Math.PI).toFloat()
        if (x < -Math.PI) x += (2 * Math.PI).toFloat()
        return x
    }

    private fun transpose3(m: FloatArray): FloatArray =
        floatArrayOf(
            m[0], m[3], m[6],
            m[1], m[4], m[7],
            m[2], m[5], m[8]
        )

    private fun multiply3(a: FloatArray, b: FloatArray): FloatArray {
        val o = FloatArray(9)
        for (r in 0..2) {
            for (c in 0..2) {
                o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c]
            }
        }
        return o
    }

    companion object {
        const val MAX_TILT = 45f
        /** Fraction of remaining error closed per sample (mirrors Swift). */
        const val SMOOTHING = 0.7f
        /** Gyro extrapolation horizon in seconds (mirrors Swift). */
        const val PREDICTION_INTERVAL = 0.04f
        /** Auto-recenter washout time constant in seconds. */
        const val RECENTER_TAU_S = 15f
        /** Below this angular rate (rad/s) the device counts as still. */
        const val STILL_THRESHOLD_RAD_S = 0.15f
    }
}
