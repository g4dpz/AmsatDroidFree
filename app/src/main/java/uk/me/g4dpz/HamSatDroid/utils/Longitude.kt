// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.utils

import java.math.BigDecimal
import java.math.MathContext

class Longitude : Angle() {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other.javaClass != javaClass) return false
        return other.hashCode() == hashCode()
    }

    override fun hashCode(): Int = PRIME + (radians?.hashCode() ?: 0)

    companion object {
        private const val PRIME = 29

        /** @param degrees input */
        @JvmStatic
        fun fromDegrees(degrees: Double): Longitude = Longitude().also {
            it.radians = degreesToRadians(BigDecimal(degrees), MathContext.DECIMAL128)
        }
    }
}
