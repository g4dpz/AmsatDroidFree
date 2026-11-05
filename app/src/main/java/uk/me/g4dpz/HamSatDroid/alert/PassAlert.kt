// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

import uk.me.g4dpz.HamSatDroid.domain.PassSummary

/**
 * A reminder the user has set for one pass. It carries everything the notification and the spoken announcement need,
 * so a scheduled alert survives an app restart without recalculating the pass.
 */
data class PassAlert(
    val catnum: Int,
    val satName: String,
    val aosMillis: Long,
    val losMillis: Long,
    val maxElDeg: Double,
    val aosAz: Int,
    val losAz: Int,
) {
    /** A stable id for one pass of one satellite (the AOS to the second), used as the notification and request code. */
    val id: Int get() = (catnum.toLong() * 31 + aosMillis / 1000).hashCode()

    fun hasEnded(nowMillis: Long): Boolean = nowMillis >= losMillis

    companion object {
        fun from(pass: PassSummary) = PassAlert(
            catnum = pass.catnum,
            satName = pass.satName,
            aosMillis = pass.start.toEpochMilli(),
            losMillis = pass.end.toEpochMilli(),
            maxElDeg = pass.maxElevationDeg,
            aosAz = pass.aosAzimuthDeg,
            losAz = pass.losAzimuthDeg,
        )
    }
}
