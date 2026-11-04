package uk.me.g4dpz.HamSatDroid.aim

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot

/** Where the device is pointing, in true-north/up coordinates. [azimuthValid] is false when the axis is near vertical. */
data class Pointing(val azimuthDeg: Double, val elevationDeg: Double, val azimuthValid: Boolean)

/**
 * Turns a device orientation into the direction a chosen axis of the device points, in true-north coordinates.
 * Pure, so it is unit tested.
 *
 * The rotation matrix is the world-from-device matrix Android gives through `getRotationMatrixFromVector` /
 * `getRotationMatrix`: it maps a vector in device coordinates to the world ENU frame (x east, y north, z up). The
 * device axes are x right, y top edge, z out of the screen.
 */
object PointingMath {

    /** The device-space direction of each pointing axis. */
    private fun axisVector(axis: PointingAxis): FloatArray = when (axis) {
        PointingAxis.TOP_EDGE -> floatArrayOf(0f, 1f, 0f) // +Y
        PointingAxis.BACK -> floatArrayOf(0f, 0f, -1f) // -Z (the back faces away from the screen)
    }

    /**
     * The azimuth and elevation of [axis] given a world-from-device [rotationMatrix] (row-major 3x3 or 4x4 as Android
     * supplies) and the magnetic [declinationDeg] to correct to true north. When the axis points within about a
     * degree of straight up or down the azimuth is meaningless, so [Pointing.azimuthValid] is false.
     */
    fun pointing(rotationMatrix: FloatArray, axis: PointingAxis, declinationDeg: Double): Pointing {
        val v = axisVector(axis)
        // world = R * deviceVector; R is row-major, so each world component is a row dotted with v.
        val east = rotationMatrix[0] * v[0] + rotationMatrix[1] * v[1] + rotationMatrix[2] * v[2]
        val north = rotationMatrix[3] * v[0] + rotationMatrix[4] * v[1] + rotationMatrix[5] * v[2]
        val up = rotationMatrix[6] * v[0] + rotationMatrix[7] * v[1] + rotationMatrix[8] * v[2]

        val horizontal = hypot(east.toDouble(), north.toDouble())
        val elevation = Math.toDegrees(atan2(up.toDouble(), horizontal))
        val azimuthValid = horizontal > 0.02 // about 1 degree from vertical
        val magneticAz = Math.toDegrees(atan2(east.toDouble(), north.toDouble())) // 0 = north, clockwise
        val azimuth = HeadingMath.trueHeading(magneticAz, declinationDeg)
        return Pointing(azimuth, elevation, azimuthValid)
    }

    /** Elevation only, from the same matrix; always defined. Used when only the tilt is needed. */
    fun elevationOf(rotationMatrix: FloatArray, axis: PointingAxis): Double {
        val v = axisVector(axis)
        val east = rotationMatrix[0] * v[0] + rotationMatrix[1] * v[1] + rotationMatrix[2] * v[2]
        val north = rotationMatrix[3] * v[0] + rotationMatrix[4] * v[1] + rotationMatrix[5] * v[2]
        val up = rotationMatrix[6] * v[0] + rotationMatrix[7] * v[1] + rotationMatrix[8] * v[2]
        return Math.toDegrees(asin((up / hypot(hypot(east, north), up)).toDouble()))
    }
}
