package com.example.juki.game

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class TiltControllerTest {
    @Test
    fun gravityIsMappedToScreenDirectionsForEveryRotation() {
        val gravity = 9.80665f
        assertTilt(-1f, 0f, gravity, 0f, Surface.ROTATION_0)
        assertTilt(0f, 1f, gravity, 0f, Surface.ROTATION_90)
        assertTilt(1f, 0f, gravity, 0f, Surface.ROTATION_180)
        assertTilt(0f, -1f, gravity, 0f, Surface.ROTATION_270)

        assertTilt(0f, 1f, 0f, gravity, Surface.ROTATION_0)
        assertTilt(1f, 0f, 0f, gravity, Surface.ROTATION_90)
        assertTilt(0f, -1f, 0f, gravity, Surface.ROTATION_180)
        assertTilt(-1f, 0f, 0f, gravity, Surface.ROTATION_270)
    }

    @Test
    fun smallNoiseAndInvalidValuesDoNotCauseDrift() {
        assertTilt(0f, 0f, 0.1f, -0.1f, Surface.ROTATION_0)
        assertTilt(0f, 0f, Float.NaN, 1f, Surface.ROTATION_0)
        assertTilt(0f, 0f, 1f, Float.POSITIVE_INFINITY, Surface.ROTATION_0)
    }

    @Test
    fun excessiveReadingsAreClamped() {
        assertTilt(-1f, 1f, 100f, 100f, Surface.ROTATION_0)
    }

    private fun assertTilt(
        expectedX: Float,
        expectedY: Float,
        sensorX: Float,
        sensorY: Float,
        rotation: Int
    ) {
        val (x, y) = TiltController.screenTilt(sensorX, sensorY, rotation)
        assertEquals(expectedX, x, 0.0001f)
        assertEquals(expectedY, y, 0.0001f)
    }
}
