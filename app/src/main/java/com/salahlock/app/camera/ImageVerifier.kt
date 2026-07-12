package com.salahlock.app.camera

import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context
import com.salahlock.app.data.model.VerificationResult
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Rule-based image verifier — no ML model needed for MVP.
 *
 * Intent: Create FRICTION before accessing distracting apps.
 * Not a theological proof of prayer.
 *
 * Checks:
 *  1. Brightness — is there enough light to see?
 *  2. Camera orientation — is phone pointing DOWN at floor level?
 *  3. Texture/edge density — is there a patterned surface visible?
 */
object ImageVerifier {

    private const val MIN_BRIGHTNESS = 35.0   // 0-255
    private const val MIN_EDGE_DENSITY = 0.04 // fraction of pixels with strong edges
    private const val MAX_TILT_ANGLE_DEG = 45.0 // phone must be at least 45° tilted

    fun verify(bitmap: Bitmap, gravityValues: FloatArray?): VerificationResult {
        // 1. Check brightness
        val brightness = computeAverageBrightness(bitmap)
        if (brightness < MIN_BRIGHTNESS) {
            return VerificationResult.TooDark
        }

        // 2. Check camera orientation (accelerometer values)
        if (gravityValues != null) {
            val tiltAngle = computeTiltAngle(gravityValues)
            if (tiltAngle < MAX_TILT_ANGLE_DEG) {
                return VerificationResult.NotPointingDown
            }
        }

        // 3. Check texture / edge density
        val edgeDensity = computeEdgeDensity(bitmap)
        if (edgeDensity < MIN_EDGE_DENSITY) {
            return VerificationResult.NoTexture
        }

        return VerificationResult.Verified
    }

    /** Returns average luminance of the image in range 0-255 */
    private fun computeAverageBrightness(bitmap: Bitmap): Double {
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        var total = 0L
        val pixels = IntArray(64 * 64)
        scaled.getPixels(pixels, 0, 64, 0, 0, 64, 64)
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Standard luminance formula
            total += (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        scaled.recycle()
        return total.toDouble() / pixels.size
    }

    /**
     * Computes the fraction of pixels with strong edges using a simple Sobel-like approximation.
     * A prayer mat or any textured surface will have many edges.
     * A blank white wall or plain surface will have almost none.
     */
    private fun computeEdgeDensity(bitmap: Bitmap): Double {
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val width = scaled.width
        val height = scaled.height
        val gray = Array(height) { y ->
            IntArray(width) { x ->
                val pixel = scaled.getPixel(x, y)
                (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
            }
        }

        var edgeCount = 0
        val threshold = 25

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val gx = -gray[y - 1][x - 1] + gray[y - 1][x + 1] +
                        -2 * gray[y][x - 1] + 2 * gray[y][x + 1] +
                        -gray[y + 1][x - 1] + gray[y + 1][x + 1]
                val gy = gray[y - 1][x - 1] + 2 * gray[y - 1][x] + gray[y - 1][x + 1] +
                        -gray[y + 1][x - 1] - 2 * gray[y + 1][x] - gray[y + 1][x + 1]
                val magnitude = sqrt((gx * gx + gy * gy).toDouble())
                if (magnitude > threshold) edgeCount++
            }
        }

        scaled.recycle()
        return edgeCount.toDouble() / (width * height)
    }

    /**
     * Compute tilt angle of phone from gravity sensor values.
     * Returns degrees from vertical — 90° = phone flat facing down.
     */
    private fun computeTiltAngle(gravity: FloatArray): Double {
        val x = gravity[0]
        val y = gravity[1]
        val z = gravity[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        if (magnitude == 0.0) return 0.0
        // z-component when phone faces up = 9.8 (gravity), faces down = -9.8
        // Tilt towards floor: z becomes negative, |z|/magnitude approaches 1
        val tiltRadians = Math.acos(abs(z) / magnitude)
        return Math.toDegrees(tiltRadians)
    }

    fun getFeedbackMessage(result: VerificationResult): String = when (result) {
        is VerificationResult.TooDark ->
            "Too dark — please move to a brighter area or turn on a light."
        is VerificationResult.NotPointingDown ->
            "Point your camera down at your prayer mat."
        is VerificationResult.NoTexture ->
            "No prayer mat detected — make sure your mat fills most of the frame."
        is VerificationResult.Verified -> "Verified!"
        is VerificationResult.Error -> result.message
    }
}

/** Reads gravity sensor values for tilt detection. */
class GravitySensorReader(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    var latestGravity: FloatArray? = null

    fun start() {
        gravitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        latestGravity = event.values.copyOf()
    }
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
