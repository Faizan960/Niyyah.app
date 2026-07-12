package com.salahlock.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.atan2

class CompassHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    private val _accuracy = MutableStateFlow("High")
    val accuracy: StateFlow<String> = _accuracy.asStateFlow()

    private var lastAzimuth = 0f
    private val alpha = 0.15f // Low pass filter factor for smoothness

    fun start() {
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            var azimuthInRadians = orientation[0]
            var azimuthInDegrees = Math.toDegrees(azimuthInRadians.toDouble()).toFloat()

            // Map to 0-360
            if (azimuthInDegrees < 0) {
                azimuthInDegrees += 360f
            }

            // Normalize azimuthInDegrees to be within 180 degrees of lastAzimuth
            // to allow continuous scrolling in Compose without snap jumps
            while (azimuthInDegrees - lastAzimuth > 180f) {
                azimuthInDegrees -= 360f
            }
            while (azimuthInDegrees - lastAzimuth < -180f) {
                azimuthInDegrees += 360f
            }

            val smoothedAzimuth = (alpha * azimuthInDegrees) + ((1 - alpha) * lastAzimuth)

            lastAzimuth = smoothedAzimuth
            _azimuth.update { smoothedAzimuth }

            // Approximation of accuracy based on event accuracy
            val acc = when (event.accuracy) {
                SensorManager.SENSOR_STATUS_UNRELIABLE -> "Low"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
                else -> "High"
            }
            _accuracy.update { acc }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val acc = when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "Low"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
            else -> _accuracy.value
        }
        _accuracy.update { acc }
    }
}
