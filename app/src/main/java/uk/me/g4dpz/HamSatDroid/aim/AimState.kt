package uk.me.g4dpz.HamSatDroid.aim

import kotlin.math.abs

/** A direction to aim at: azimuth and elevation in degrees, true north, up positive. */
data class AimTarget(val azimuthDeg: Double, val elevationDeg: Double)

/** How far the aim is from the target, and whether it is on target. */
sealed interface AimStatus {
    /** Both differences are within the tolerance. */
    data object OnTarget : AimStatus

    /** Still aiming: how far to turn ([deltaAzDeg], positive = clockwise) and tilt ([deltaElDeg], positive = raise). */
    data class Aiming(val deltaAzDeg: Double, val deltaElDeg: Double) : AimStatus

    /** The satellite is below the horizon; the UI should show the next rise instead of aiming instructions. */
    data object BelowHorizon : AimStatus
}

/**
 * Compares where the device points with where the satellite is and says how to move. Pure, so it is unit tested; the
 * UI turns [AimStatus.Aiming] into words ("turn right 20°, raise 10°").
 */
object AimState {

    /** Both azimuth and elevation must be within this many degrees to count as on target. */
    const val TOLERANCE_DEG = 5.0

    fun compute(pointing: Pointing, target: AimTarget): AimStatus {
        if (target.elevationDeg < 0.0) return AimStatus.BelowHorizon

        val deltaEl = target.elevationDeg - pointing.elevationDeg
        // When the device is near vertical the azimuth is meaningless, so judge on elevation alone until it tilts down.
        val deltaAz = if (pointing.azimuthValid) HeadingMath.signedDelta(pointing.azimuthDeg, target.azimuthDeg) else 180.0

        return if (abs(deltaAz) <= TOLERANCE_DEG && abs(deltaEl) <= TOLERANCE_DEG) {
            AimStatus.OnTarget
        } else {
            AimStatus.Aiming(deltaAz, deltaEl)
        }
    }
}
