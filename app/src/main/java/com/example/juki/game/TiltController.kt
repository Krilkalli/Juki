package com.example.juki.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs

class TiltController(
    context: Context,
    private val rotation: () -> Int,
    private val onTilt: (Float, Float) -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var running = false
    private var initialized = false
    private var gravityX = 0f
    private var gravityY = 0f

    fun start(): Boolean {
        if (running) return true
        val selectedSensor = sensor ?: return false
        initialized = false
        running = sensorManager?.registerListener(
            this, selectedSensor, SensorManager.SENSOR_DELAY_GAME
        ) == true
        return running
    }

    fun stop() {
        running = false
        sensorManager?.unregisterListener(this)
        initialized = false
        onTilt(0f, 0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.sensor.type != sensor?.type) return
        val x = event.values[0]
        val y = event.values[1]
        if (!x.isFinite() || !y.isFinite()) return

        // Smooth accelerometer readings to reduce the effect of shaking.
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && initialized) {
            gravityX = gravityX * 0.8f + x * 0.2f
            gravityY = gravityY * 0.8f + y * 0.2f
        } else {
            gravityX = x
            gravityY = y
            initialized = true
        }

        val (screenX, screenY) = screenTilt(gravityX, gravityY, rotation())
        onTilt(screenX, screenY)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        // Convert sensor axes to the game's screen axes: right and down.
        internal fun screenTilt(x: Float, y: Float, rotation: Int): Pair<Float, Float> {
            if (!x.isFinite() || !y.isFinite()) return 0f to 0f
            val (screenX, screenY) = when (rotation) {
                Surface.ROTATION_90 -> y to x
                Surface.ROTATION_180 -> x to -y
                Surface.ROTATION_270 -> -y to -x
                else -> -x to y
            }
            return normalize(screenX) to normalize(screenY)
        }

        private fun normalize(value: Float): Float {
            val tilt = (value / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
            return if (abs(tilt) < 0.04f) 0f else tilt
        }
    }
}
