# DuoFold — iPhone Duo fold/opening animation for Android

## Model
Fixed interface plane at zero tilt · stationary eye on the plane normal
(320mm default) · glass rotates around the hinge edge (runtime-resolved,
never hardcoded) · per-pixel ray eye→glass→plane, gap-proportional disk
blur + darken, black on miss.

## Project layout
```
app/src/main/java/com/example/duofold/
  MainActivity.kt
  ui/FoldEffect.kt       Modifier.foldEffect() — graphicsLayer + RuntimeShader
  ui/FoldParameters.kt   viewDistanceMm, blur/darken tuning
  ui/DemoContentView.kt  realistic demo screen (header + card + list)
  ui/ContentView.kt      root + Recalibrate button + manual tilt slider
  sensors/FoldMotionModel.kt  rotation-vector, zero-pose calibration, hinge
app/src/main/res/raw/duo_fold.agsl   the shader
```

## Build
1. Open this folder in Android Studio (Hedgehog or later).
2. Let it provision Gradle 8.7 + AGP 8.5.2 + SDK 34.
3. Run on a physical device (API 33+) — the emulator has no rotation-vector
   sensor, so the app boots into manual slider mode there.
4. If Gradle wrapper jars are missing, run `gradle wrapper` once in this dir.

## Use / tune
- Slider: -45°..+45°. Sign flips the hinge (L/R shown in the panel).
- Switch toggles Sensor vs Manual (disabled without a sensor).
- Recalibrate re-zeroes the pose AND resets manual tilt to 0.
- Aesthetics live in `FoldParameters` + `duo_fold.agsl` uniforms:
  `viewDistanceMm` (320), `blurPerMm` (0.55), `maxBlurRadiusPx` (24),
  `darkenPerMm` (0.012), `maxDarken` (0.55).
- Known iteration points: tilt/hinge sign conventions, tap count vs perf
  (12 taps now), view-distance/blur/darken constants on a real device.

## 💖 Support My Work

If you find this project useful, please consider supporting its development!

[![ko-fi](https://ko-fi.com)](https://ko-fi.com/xatomicx)
