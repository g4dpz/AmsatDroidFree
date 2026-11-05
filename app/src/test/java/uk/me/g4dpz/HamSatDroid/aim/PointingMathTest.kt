// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PointingMathTest {

    // Identity world-from-device: device x->east, y->north, z->up.
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    @Test
    fun `top edge with identity points north and level`() {
        val p = PointingMath.pointing(identity, PointingAxis.TOP_EDGE, declinationDeg = 0.0)
        assertEquals(0.0, p.azimuthDeg, 1e-6)
        assertEquals(0.0, p.elevationDeg, 1e-6)
        assertTrue(p.azimuthValid)
    }

    @Test
    fun `back axis with identity points straight down`() {
        // the back faces -z (into the table when the screen is up), so elevation is -90 and azimuth undefined
        val p = PointingMath.pointing(identity, PointingAxis.BACK, declinationDeg = 0.0)
        assertEquals(-90.0, p.elevationDeg, 1e-6)
        assertFalse(p.azimuthValid)
    }

    @Test
    fun `rotating the device 90 degrees clockwise sends the top edge east`() {
        // world-from-device that maps device +Y to world east: top edge now points east (azimuth 90)
        val m = floatArrayOf(
            0f, 1f, 0f,   // east = device y
            -1f, 0f, 0f,  // north = -device x
            0f, 0f, 1f,   // up = device z
        )
        val p = PointingMath.pointing(m, PointingAxis.TOP_EDGE, declinationDeg = 0.0)
        assertEquals(90.0, p.azimuthDeg, 1e-6)
        assertEquals(0.0, p.elevationDeg, 1e-6)
    }

    @Test
    fun `raising the top edge gives positive elevation`() {
        // tilt back 30°: device +Y maps partly up
        val a = Math.toRadians(30.0)
        val cos = Math.cos(a).toFloat(); val sin = Math.sin(a).toFloat()
        val m = floatArrayOf(
            1f, 0f, 0f,
            0f, cos, -sin,
            0f, sin, cos,
        )
        val p = PointingMath.pointing(m, PointingAxis.TOP_EDGE, declinationDeg = 0.0)
        assertEquals(30.0, p.elevationDeg, 1e-5)
        assertEquals(0.0, p.azimuthDeg, 1e-5)
    }

    @Test
    fun `declination is applied to the pointing azimuth`() {
        val p = PointingMath.pointing(identity, PointingAxis.TOP_EDGE, declinationDeg = 10.0)
        assertEquals(10.0, p.azimuthDeg, 1e-6)
    }

    @Test
    fun `azimuth is marked invalid when the axis is near vertical`() {
        // top edge pointing almost straight up
        val a = Math.toRadians(89.5)
        val cos = Math.cos(a).toFloat(); val sin = Math.sin(a).toFloat()
        val m = floatArrayOf(1f, 0f, 0f, 0f, cos, -sin, 0f, sin, cos)
        val p = PointingMath.pointing(m, PointingAxis.TOP_EDGE, declinationDeg = 0.0)
        assertFalse(p.azimuthValid)
        assertEquals(89.5, p.elevationDeg, 1e-4)
    }
}
