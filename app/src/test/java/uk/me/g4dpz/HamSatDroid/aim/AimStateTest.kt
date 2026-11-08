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
    fun `pointing straight up is judged on elevation alone`() {
        // The azimuth is only invalid within about a degree of vertical, so the pointing elevation is ~89 or more.
        // A satellite near the zenith is then on target whatever azimuth the matrix happens to give.
        val overhead = AimState.compute(Pointing(37.0, 89.5, azimuthValid = false), AimTarget(123.0, 87.0))
        assertEquals(AimStatus.OnTarget, overhead)

        // A lower satellite needs lowering; no turn is named (0, not an arbitrary 180) because none can be known.
        val lower = aiming(AimState.compute(Pointing(37.0, 89.5, azimuthValid = false), AimTarget(123.0, 45.0)))
        assertEquals(0.0, lower.deltaAzDeg, 1e-9)
        assertEquals(-44.5, lower.deltaElDeg, 1e-9)
        assertTrue(lower.deltaElDeg < 0)
    }

    @Test
    fun `tolerance boundary is inclusive`() {
        val status = AimState.compute(
            Pointing(0.0, 0.0, true), AimTarget(AimState.TOLERANCE_DEG, AimState.TOLERANCE_DEG),
        )
        assertEquals(AimStatus.OnTarget, status)
    }
}
