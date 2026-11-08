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
    /**
     * A stable id for one stored alert (the satellite and the AOS to the second), used as the notification id and
     * the alarm request code. It is not how a pass is recognised: see [isSamePass].
     */
    val id: Int get() = (catnum.toLong() * 31 + aosMillis / 1000).hashCode()

    fun hasEnded(nowMillis: Long): Boolean = nowMillis >= losMillis

    /**
     * Whether this alert is for the pass of [catnum] running from [aosMillis] to [losMillis].
     *
     * The same pass predicted from a different start time comes out a few seconds different at each end (the
     * predictor refines to 5 seconds from wherever it started), so two predictions of one pass are matched by
     * overlap of their `[aos, los)` intervals rather than by an exact time. Passes of one satellite are separated
     * by most of an orbit, so overlap cannot confuse two different passes.
     */
    fun isSamePass(catnum: Int, aosMillis: Long, losMillis: Long): Boolean =
        this.catnum == catnum && this.aosMillis < losMillis && aosMillis < this.losMillis

    fun isSamePass(other: PassAlert): Boolean = isSamePass(other.catnum, other.aosMillis, other.losMillis)

    fun isSamePass(pass: PassSummary): Boolean = isSamePass(pass.catnum, pass.start.toEpochMilli(), pass.end.toEpochMilli())

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
