// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.aim

import kotlin.math.abs

/**
 * Decides which compass readings are worth passing on to the screen.
 *
 * The sensors report many times a second, even for a device lying still, and every reading passed on makes the sky
 * screen do work. A reading is passed on when it is the first, when the accuracy changed (the calibration hint depends
 * on it), or when at least [minIntervalMillis] have passed since the last one passed on **and** the rotation matrix has
 * moved by at least [minMatrixChange] in some element (about half a degree) since then.
 *
 * It compares with the last reading passed on, not the last one seen, so a slow drift still gets through. The matrix,
 * not just the heading, is compared because tilting the device changes where it points without changing the heading,
 * and the aiming aid needs that. The sensor filtering is not affected: the caller still smooths every reading.
 */
class OrientationThrottle(
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val minMatrixChange: Float = DEFAULT_MIN_MATRIX_CHANGE,
) {
    private var lastMatrix: FloatArray? = null
    private var lastAccuracy = Int.MIN_VALUE
    private var lastMillis = 0L

    /**
     * Whether the reading made up of [matrix] (nine elements) and [accuracy] at [nowMillis] should be passed on. When
     * it returns true the reading becomes the one later readings are compared with; [matrix] is copied, so the caller
     * may reuse its array.
     */
    fun shouldEmit(matrix: FloatArray, accuracy: Int, nowMillis: Long): Boolean {
        val last = lastMatrix
        val emit = last == null ||
            accuracy != lastAccuracy ||
            (nowMillis - lastMillis >= minIntervalMillis && largestChange(matrix, last) >= minMatrixChange)
        if (emit) {
            if (last == null) lastMatrix = matrix.copyOf() else matrix.copyInto(last)
            lastAccuracy = accuracy
            lastMillis = nowMillis
        }
        return emit
    }

    private fun largestChange(a: FloatArray, b: FloatArray): Float {
        var largest = 0f
        for (i in 0 until minOf(a.size, b.size)) largest = maxOf(largest, abs(a[i] - b[i]))
        return largest
    }

    companion object {
        /** At most 20 readings a second reach the screen, however fast the sensor reports. */
        const val DEFAULT_MIN_INTERVAL_MILLIS = 50L

        /** About half a degree: an element of a rotation matrix moves by at most sin(0.5 degrees) = 0.0087 for it. */
        const val DEFAULT_MIN_MATRIX_CHANGE = 0.009f
    }
}
