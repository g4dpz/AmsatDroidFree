package uk.me.g4dpz.HamSatDroid.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingMathTest {

    @Test
    fun `normalise brings angles into 0 to 360`() {
        assertEquals(10.0, HeadingMath.normalise(370.0), 1e-9)
        assertEquals(350.0, HeadingMath.normalise(-10.0), 1e-9)
        assertEquals(0.0, HeadingMath.normalise(360.0), 1e-9)
    }

    @Test
    fun `signed delta takes the shorter way round`() {
        assertEquals(20.0, HeadingMath.signedDelta(350.0, 10.0), 1e-9)
        assertEquals(-20.0, HeadingMath.signedDelta(10.0, 350.0), 1e-9)
        assertEquals(180.0, HeadingMath.signedDelta(0.0, 180.0), 1e-9)
        assertEquals(90.0, HeadingMath.signedDelta(0.0, 90.0), 1e-9)
    }

    @Test
    fun `smoothing a heading crosses 0 and 360 correctly`() {
        // from 350 toward 10 should move up through 0, not down through 180
        val half = HeadingMath.smoothHeading(350.0, 10.0, 0.5)
        assertEquals(0.0, half, 1e-9)
        val quarter = HeadingMath.smoothHeading(350.0, 10.0, 0.25)
        assertEquals(355.0, quarter, 1e-9)
    }

    @Test
    fun `smoothing with alpha 1 snaps to the measurement`() {
        assertEquals(42.0, HeadingMath.smoothHeading(300.0, 42.0, 1.0), 1e-9)
    }

    @Test
    fun `true heading adds declination and normalises`() {
        assertEquals(5.0, HeadingMath.trueHeading(0.0, 5.0), 1e-9) // east declination
        assertEquals(355.0, HeadingMath.trueHeading(0.0, -5.0), 1e-9) // west declination wraps
        assertEquals(10.0, HeadingMath.trueHeading(355.0, 15.0), 1e-9)
    }

    @Test
    fun `flat and upright are decided from gravity with hysteresis`() {
        // screen up: gravity almost all on z -> flat
        assertFalse(HeadingMath.isUpright(0f, 0f, 9.8f, wasUpright = false))
        // held vertical: gravity in the x-y plane -> upright
        assertTrue(HeadingMath.isUpright(0f, 9.8f, 0f, wasUpright = false))
        // in the dead band (about 30°) the previous state is kept
        val gx = 0f
        val gyz = 9.8
        val tilt = 30.0
        val gy = (gyz * Math.sin(Math.toRadians(tilt))).toFloat()
        val gz = (gyz * Math.cos(Math.toRadians(tilt))).toFloat()
        assertTrue(HeadingMath.isUpright(gx, gy, gz, wasUpright = true))
        assertFalse(HeadingMath.isUpright(gx, gy, gz, wasUpright = false))
    }

    @Test
    fun `mean heading averages across the wrap point`() {
        assertEquals(0.0, HeadingMath.meanHeading(350.0, 10.0), 1e-6)
        assertEquals(45.0, HeadingMath.meanHeading(0.0, 90.0), 1e-6)
    }
}
