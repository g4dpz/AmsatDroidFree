// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.utils

import java.math.BigDecimal
import java.math.MathContext

open class Angle() {

    /** Representation of the angle in radians. */
    internal var radians: BigDecimal? = null

    /** @param radiansIn angle in radians */
    constructor(radiansIn: Double) : this() {
        radians = BigDecimal(radiansIn)
    }

    override fun hashCode(): Int = PRIME + (radians?.hashCode() ?: 0)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Angle) return false
        return other.hashCode() == hashCode()
    }

    fun setRadians(radiansIn: Double) {
        radians = BigDecimal(radiansIn)
    }

    fun getRadians(): Double = radians!!.toDouble()

    fun toDegrees(): Double = radiansToDegrees(radians!!, MathContext.DECIMAL128).toDouble()

    companion object {
        private const val PRIME = 7
        private val BIG_PI = BigDecimal(Math.PI)

        @JvmStatic
        fun fromDegrees(degrees: Double): Angle = Angle().also {
            it.radians = degreesToRadians(BigDecimal(degrees), MathContext.DECIMAL128)
        }

        @JvmStatic
        fun radiansToDegrees(radians: Double): Double =
            radiansToDegrees(BigDecimal(radians), MathContext.DECIMAL128).toDouble()

        @JvmStatic
        fun degreesToRadians(degrees: Double): Double =
            degreesToRadians(BigDecimal(degrees), MathContext.DECIMAL128).toDouble()

        internal fun radiansToDegrees(radians: BigDecimal, mc: MathContext): BigDecimal =
            radians.multiply(BigDecimal(180)).divide(BIG_PI, mc)

        internal fun degreesToRadians(degrees: BigDecimal, mc: MathContext): BigDecimal =
            degrees.divide(BigDecimal(180), mc).multiply(BIG_PI)
    }
}
