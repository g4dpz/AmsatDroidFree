// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AimStateTest {

    private fun aiming(status: AimStatus) = status as AimStatus.Aiming

    @Test
    fun `on target when both differences are within tolerance`() {
        val status = AimState.compute(
            Pointing(100.0, 30.0, azimuthValid = true), AimTarget(103.0, 27.0),
        )
        assertEquals(AimStatus.OnTarget, status)
    }

    @Test
    fun `turn right is a positive azimuth delta`() {
        val d = aiming(AimState.compute(Pointing(100.0, 30.0, true), AimTarget(120.0, 30.0)))
        assertEquals(20.0, d.deltaAzDeg, 1e-9)
        assertEquals(0.0, d.deltaElDeg, 1e-9)
    }

    @Test
    fun `turn left is a negative azimuth delta across the wrap point`() {
        val d = aiming(AimState.compute(Pointing(10.0, 20.0, true), AimTarget(350.0, 20.0)))
        assertEquals(-20.0, d.deltaAzDeg, 1e-9)
    }

    @Test
    fun `raise and lower come from the elevation delta`() {
        val raise = aiming(AimState.compute(Pointing(50.0, 10.0, true), AimTarget(50.0, 40.0)))
        assertEquals(30.0, raise.deltaElDeg, 1e-9)
        val lower = aiming(AimState.compute(Pointing(50.0, 40.0, true), AimTarget(50.0, 10.0)))
        assertEquals(-30.0, lower.deltaElDeg, 1e-9)
    }

    @Test
    fun `below the horizon is reported instead of aiming`() {
        assertEquals(
            AimStatus.BelowHorizon,
            AimState.compute(Pointing(50.0, 10.0, true), AimTarget(120.0, -5.0)),
        )
    }

    @Test
    fun `an invalid pointing azimuth never counts as on target`() {
        // elevation matches but azimuth is unknown (device near vertical): keep aiming
        val status = AimState.compute(Pointing(0.0, 45.0, azimuthValid = false), AimTarget(123.0, 45.0))
        assertTrue(status is AimStatus.Aiming)
        assertEquals(180.0, aiming(status).deltaAzDeg, 1e-9)
    }

    @Test
    fun `tolerance boundary is inclusive`() {
        val status = AimState.compute(
            Pointing(0.0, 0.0, true), AimTarget(AimState.TOLERANCE_DEG, AimState.TOLERANCE_DEG),
        )
        assertEquals(AimStatus.OnTarget, status)
    }
}
