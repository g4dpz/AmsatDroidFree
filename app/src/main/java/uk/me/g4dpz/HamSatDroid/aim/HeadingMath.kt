package uk.me.g4dpz.HamSatDroid.aim

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The angle maths behind the compass and the aiming aid, kept pure so it can be unit tested without sensors.
 *
 * Angles are in degrees unless noted. Azimuth is 0 at true north, increasing clockwise (east 90). Headings are
 * normalised to 0..360; signed differences are in -180..180.
 */
object HeadingMath {

    /** Brings [degrees] into 0..360. */
    fun normalise(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0

    /** The shortest signed turn from [from] to [to], in -180..180 (positive clockwise). */
    fun signedDelta(from: Double, to: Double): Double {
        var d = normalise(to) - normalise(from)
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    /**
     * Low-pass smoothing of a heading that crosses 0/360 correctly: moves [alpha] of the way from [previous] toward
     * [measurement] along the shorter arc. [alpha] 1.0 snaps to the measurement, small values smooth more.
     */
    fun smoothHeading(previous: Double, measurement: Double, alpha: Double): Double =
        normalise(previous + alpha * signedDelta(previous, measurement))

    /** Ordinary low-pass smoothing for a non-circular value such as elevation. */
    fun smooth(previous: Double, measurement: Double, alpha: Double): Double =
        previous + alpha * (measurement - previous)

    /** Adds the magnetic [declinationDeg] to a magnetic [magneticHeadingDeg] to give a true-north heading. */
    fun trueHeading(magneticHeadingDeg: Double, declinationDeg: Double): Double =
        normalise(magneticHeadingDeg + declinationDeg)

    /**
     * Whether the device should be treated as held upright rather than flat, from the gravity direction in device
     * coordinates (z is out of the screen). [wasUpright] is the previous decision; the two thresholds give hysteresis
     * so the choice does not flip when the device is near 45°.
     *
     * Flat means the screen faces up or down (gravity mostly along z); upright means the screen faces the horizon
     * (gravity mostly in the x-y plane). The tilt here is the angle of the screen from horizontal.
     */
    fun isUpright(
        gravityX: Float, gravityY: Float, gravityZ: Float, wasUpright: Boolean,
        flatBelowDeg: Double = 25.0, uprightAboveDeg: Double = 35.0,
    ): Boolean {
        val horizontal = sqrt((gravityX * gravityX + gravityY * gravityY).toDouble())
        val tiltDeg = Math.toDegrees(kotlin.math.atan2(horizontal, abs(gravityZ.toDouble())))
        return when {
            tiltDeg >= uprightAboveDeg -> true
            tiltDeg <= flatBelowDeg -> false
            else -> wasUpright // in the dead band, keep the current state
        }
    }

    /** The circular mean of two headings, used lightly in tests and for averaging equal-weight sources. */
    fun meanHeading(a: Double, b: Double): Double {
        val x = cos(Math.toRadians(a)) + cos(Math.toRadians(b))
        val y = sin(Math.toRadians(a)) + sin(Math.toRadians(b))
        return normalise(Math.toDegrees(kotlin.math.atan2(y, x)))
    }
}
