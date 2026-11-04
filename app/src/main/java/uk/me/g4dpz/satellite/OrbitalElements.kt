package uk.me.g4dpz.satellite

/**
 * Mean orbital elements for one satellite, independent of how they were delivered (TLE text, Celestrak/OMM JSON).
 *
 * This is the input to [TLE.fromElements]. Unlike a TLE line pair it can carry catalogue numbers above 99999.
 *
 * The epoch is held the same way a TLE holds it, as a year plus a day of the year with its fraction, so converting
 * a TLE to elements and back loses nothing.
 *
 * Angles are in degrees, [meanMotion] in revolutions per day, [meanMotionDot] and [meanMotionDdot] as given in the
 * TLE (first derivative of mean motion in rev/day^2, as in the TLE field, and second derivative in rev/day^3),
 * [bstar] as the decoded drag term (1/earth radii).
 */
data class OrbitalElements(
    val name: String,
    val catnum: Int,
    /** Four-digit epoch year, 1957 to 2056 (the range a two-digit TLE year can express). */
    val epochYear: Int,
    /** Day of the year starting at 1.0 for 00:00 UTC on 1 January, including the fraction of the day. */
    val epochDay: Double,
    val meanMotion: Double,
    val eccentricity: Double,
    val inclination: Double,
    val raan: Double,
    val argPerigee: Double,
    val meanAnomaly: Double,
    val bstar: Double = 0.0,
    val meanMotionDot: Double = 0.0,
    val meanMotionDdot: Double = 0.0,
    val elementSetNo: Int = 0,
    val revAtEpoch: Int = 0,
) {
    /**
     * @throws IllegalArgumentException if any value is outside what the prediction code can use
     */
    fun validate() {
        require(catnum > 0) { "Catalogue number must be positive: $catnum" }
        require(epochYear in 1957..2056) { "Epoch year out of range: $epochYear" }
        require(epochDay.isFinite() && epochDay >= 1.0 && epochDay < 367.0) { "Epoch day out of range: $epochDay" }
        require(meanMotion.isFinite() && meanMotion > 0.0) { "Mean motion must be positive: $meanMotion" }
        require(eccentricity.isFinite() && eccentricity >= 0.0 && eccentricity < 1.0) {
            "Eccentricity out of range: $eccentricity"
        }
        for ((label, v) in listOf(
            "inclination" to inclination, "raan" to raan, "argument of perigee" to argPerigee,
            "mean anomaly" to meanAnomaly, "bstar" to bstar, "mean motion dot" to meanMotionDot,
            "mean motion ddot" to meanMotionDdot
        )) {
            require(v.isFinite()) { "$label is not a finite number" }
        }
    }

    companion object {
        /** Converts a parsed [TLE] back to elements, keeping every value the prediction code uses. */
        @JvmStatic
        fun from(tle: TLE): OrbitalElements = OrbitalElements(
            name = tle.name,
            catnum = tle.catnum,
            epochYear = if (tle.year < 57) 2000 + tle.year else 1900 + tle.year,
            epochDay = tle.refepoch,
            meanMotion = tle.meanmo,
            eccentricity = tle.eccn,
            inclination = tle.incl,
            raan = tle.raan,
            argPerigee = tle.argper,
            meanAnomaly = tle.meanan,
            bstar = tle.bstar,
            meanMotionDot = tle.drag,
            meanMotionDdot = tle.nddot6,
            elementSetNo = tle.setnum,
            revAtEpoch = tle.orbitnum,
        )
    }
}
