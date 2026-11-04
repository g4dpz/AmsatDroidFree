/**
    predict4java: An SDP4 / SGP4 library for satellite orbit predictions

    Copyright (C)  2004-2010  David A. B. Johnson, G4DPZ.

    This class is a Java port of one of the core elements of
    the Predict program, Copyright John A. Magliacane,
    KD2BD 1991-2003: http://www.qsl.net/kd2bd/predict.html

    Dr. T.S. Kelso is the author of the SGP4/SDP4 orbital models,
    originally written in Fortran and Pascal, and released into the
    public domain through his website (http://www.celestrak.com/). 
    Neoklis Kyriazis, 5B4AZ, later re-wrote Dr. Kelso's code in C,
    and released it under the GNU GPL in 2002.
    PREDICT's core is based on 5B4AZ's code translation efforts.

    Author: David A. B. Johnson, G4DPZ <dave@g4dpz.me.uk>

    Comments, questions and bugreports should be submitted via
    http://sourceforge.net/projects/websat/
    More details can be found at the project home page:

    http://websat.sourceforge.net

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, visit http://www.fsf.org/
 */
package uk.me.g4dpz.satellite

import java.io.Serializable
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

abstract class AbstractSatellite(final override val tle: TLE) : Satellite, Serializable {

    private var s4 = 0.0
    private var qoms24 = 0.0
    private var perigee = 0.0

    /** The depth of the eclipse, valid after the eclipse state has been determined. */
    var eclipseDepth: Double = 0.0
        private set

    /** Position vector of the satellite. Used to store the position for later calculations. */
    private val position = Vector4()

    /** Velocity vector of the satellite. Used to store the velocity for later calculations. */
    private val velocity = Vector4()

    /** Date/time at which the position and velocity were calculated */
    private var julUTC = 0.0

    /** Satellite position. Used to store the SatPos for later calculations. */
    private var satPos: SatPos? = null

    private val julEpoch: Double = juliandDateOfEpoch(tle.epoch)

    protected open fun calculateSGP4(tsince: Double) {}

    protected open fun calculateSDP4(tsince: Double) {}

    /**
     * Calculate the geodetic position of an object given its ECI position and time. It is intended to be used to
     * determine the ground track of a satellite. The calculations assume the earth to be an oblate spheroid as
     * defined in WGS '72.
     *
     * Reference: The 1992 Astronomical Almanac, page K12.
     */
    private fun calculateLatLonAlt(time: Double) {
        val sp = satPos!!
        sp.theta = atan2(position.y, position.x)
        sp.longitude = mod2PI(sp.theta - thetaGJD(time))
        val r = sqrt(sqr(position.x) + sqr(position.y))
        val e2 = FLATTENING_FACTOR * (2.0 - FLATTENING_FACTOR)
        sp.latitude = atan2(position.z, r)

        var phi: Double
        var c: Double
        var i = 0
        var converged: Boolean
        do {
            phi = sp.latitude
            c = invert(sqrt(1.0 - e2 * sqr(sin(phi))))
            sp.latitude = atan2(position.z + Satellite.EARTH_RADIUS_KM * c * e2 * sin(phi), r)
            converged = abs(sp.latitude - phi) < Satellite.EPSILON
        } while (i++ < 10 && !converged)

        sp.altitude = r / cos(sp.latitude) - Satellite.EARTH_RADIUS_KM * c
        var temp = sp.latitude
        if (temp > PI_OVER_TWO) {
            temp -= Satellite.TWO_PI
            sp.latitude = temp
        }
    }

    @Synchronized
    override fun getPosition(qth: GroundStationPosition, time: Date): SatPos {
        /* This is the stuff we need to do repetitively while tracking. */
        satPos = SatPos()
        julUTC = calcCurrentDaynum(time) + 2444238.5

        /* Convert satellite's epoch time to Julian and calculate time since epoch in minutes */
        val tsince = (julUTC - julEpoch) * MINS_PER_DAY

        if (tle.isDeepspace) {
            calculateSDP4(tsince)
        } else {
            calculateSGP4(tsince)
        }

        /* Scale position and velocity vectors to km and km/sec */
        convertSatState(position, velocity)

        /* Calculate velocity of satellite */
        magnitude(velocity)

        val squintVector = Vector4()

        /* All angles in rads. Distance in km. Velocity in km/S */
        /* Calculate satellite Azi, Ele, Range and Range-rate */
        calculateObs(julUTC, position, velocity, qth, squintVector)

        /* Calculate satellite Lat North, Lon East and Alt. */
        calculateLatLonAlt(julUTC)

        val sp = satPos!!
        sp.time = time
        sp.isEclipsed = isEclipsed()
        sp.eclipseDepth = eclipseDepth
        return sp
    }

    @Suppress("OVERRIDE_DEPRECATION")
    @Synchronized
    override fun getPosition(qth: GroundStationPosition, satPos: SatPos, time: Date) {
        satPos.copy(getPosition(qth, time))
    }

    private fun calculateObs(
        julianUTC: Double,
        positionVector: Vector4,
        velocityVector: Vector4,
        gsPos: GroundStationPosition,
        squintVector: Vector4
    ) {
        val obsPos = Vector4()
        val obsVel = Vector4()
        val range = Vector4()
        val rgvel = Vector4()

        calculateUserPosVel(julianUTC, gsPos, obsPos, obsVel)

        range.setXYZ(positionVector.x - obsPos.x, positionVector.y - obsPos.y, positionVector.z - obsPos.z)

        /* Save these values globally for calculating squint angles later... */
        squintVector.setXYZ(range.x, range.y, range.z)

        rgvel.setXYZ(velocityVector.x - obsVel.x, velocityVector.y - obsVel.y, velocityVector.z - obsVel.z)

        magnitude(range)

        val sinLat = sin(Satellite.DEG2RAD * gsPos.latitude)
        val cosLat = cos(Satellite.DEG2RAD * gsPos.latitude)
        val sinTheta = sin(gsPos.theta)
        val cosTheta = cos(gsPos.theta)
        val topS = sinLat * cosTheta * range.x + sinLat * sinTheta * range.y - cosLat * range.z
        val topE = -sinTheta * range.x + cosTheta * range.y
        val topZ = cosLat * cosTheta * range.x + cosLat * sinTheta * range.y + sinLat * range.z
        var azim = atan(-topE / topS)
        if (topS > 0.0) {
            azim += Math.PI
        }
        if (azim < 0.0) {
            azim += Satellite.TWO_PI
        }

        val sp = satPos!!
        sp.azimuth = azim
        sp.elevation = asin(topZ / range.w)
        sp.range = range.w
        sp.rangeRate = dot(range, rgvel) / range.w

        val sector = (sp.azimuth / Satellite.TWO_PI * 360.0 / 10.0).toInt()

        var elevation = (sp.elevation / Satellite.TWO_PI) * 360.0
        if (elevation > 0.0) {
            if (elevation > 90) {
                elevation = 180 - elevation
            }
        }
        sp.isAboveHorizon = (elevation - gsPos.getHorizonElevations()[sector]) > Satellite.EPSILON
    }

    override fun willBeSeen(qth: GroundStationPosition): Boolean {
        if (tle.meanmo < 1e-8) {
            return false
        }
        var lin = tle.incl
        if (lin >= 90.0) {
            lin = 180.0 - lin
        }
        val sma = 331.25 * exp(ln(1440.0 / tle.meanmo) * (2.0 / 3.0))
        val apogee = sma * (1.0 + tle.eccn) - Satellite.EARTH_RADIUS_KM
        return (acos(Satellite.EARTH_RADIUS_KM / (apogee + Satellite.EARTH_RADIUS_KM)) + (lin * Satellite.DEG2RAD)) >
            abs(qth.latitude * Satellite.DEG2RAD)
    }

    protected fun getS4(): Double = s4

    protected fun getQoms24(): Double = qoms24

    protected fun checkPerigee() {
        s4 = S
        qoms24 = QOMS2T
        if (perigee < PERIGEE_156_KM) {
            s4 = if (perigee <= 98.0) 20.0 else perigee - 78.0
            qoms24 = ((120 - s4) / Satellite.EARTH_RADIUS_KM).pow(4)
            s4 = s4 / Satellite.EARTH_RADIUS_KM + 1.0
        }
    }

    protected fun setPerigee(perigee: Double) {
        this.perigee = perigee
    }

    /**
     * Calculates the position and velocity vectors of the satellite. When observations for many ground stations have
     * to be made for one satellite, this method can be used together with [calculateSatPosForGroundStation]. This
     * gives a performance improvement relative to using the all-in-one method [getPosition].
     */
    @Synchronized
    override fun calculateSatelliteVectors(time: Date) {
        // Re-initialize, object can contain data from previous calculations
        satPos = SatPos()

        // Date/time for which the satellite position and velocity are calculated
        julUTC = calcCurrentDaynum(time) + 2444238.5

        // Calculate time since epoch in minutes
        val tsince = (julUTC - julEpoch) * MINS_PER_DAY

        // Calculations of satellite position, no ground stations involved here yet
        if (tle.isDeepspace) {
            calculateSDP4(tsince)
        } else {
            calculateSGP4(tsince)
        }

        // Scale position and velocity vectors to km and km/s
        convertSatState(position, velocity)

        // Calculate the magnitude of the velocity of satellite
        magnitude(velocity)

        val sp = satPos!!
        sp.isEclipsed = isEclipsed()
        sp.eclipseDepth = eclipseDepth
        sp.time = time
    }

    /** Calculates the ground track (sub satellite point) of the already determined satellite position. */
    @Synchronized
    override fun calculateSatelliteGroundTrack(): SatPos {
        calculateLatLonAlt(julUTC)
        return satPos!!
    }

    /**
     * Calculates the position of the satellite from the perspective of a ground station. The position and velocity of
     * the satellite must have been determined before (by [calculateSatelliteVectors]).
     */
    @Synchronized
    override fun calculateSatPosForGroundStation(gsPos: GroundStationPosition): SatPos {
        val squintVector = Vector4()
        calculateObs(julUTC, position, velocity, gsPos, squintVector)
        return satPos!!
    }

    protected fun getSatPos(): SatPos? = satPos

    /** Determines if the satellite is in sunlight. */
    private fun isEclipsed(): Boolean {
        val sunVector = calculateSunVector()

        /* Calculates satellite's eclipse status and depth */
        /* Determine partial eclipse */
        val sdEarth = asin(EARTH_RADIUS / position.w)
        val rho = Vector4.subtract(sunVector, position)
        val sdSun = asin(SOLAR_RADIUS / rho.w)
        val earth = Vector4.scalarMultiply(position, -1.0)
        val delta = Vector4.angle(sunVector, earth)

        eclipseDepth = sdEarth - sdSun - delta

        return if (sdEarth < sdSun) false else eclipseDepth >= 0
    }

    private fun calculateSunVector(): Vector4 {
        val mjd = julUTC - 2415020.0
        val year = 1900 + mjd / 365.25
        val solTime = (mjd + deltaEt(year) / SECS_PER_DAY) / 36525.0

        val m = radians(
            modulus(358.47583 + modulus(35999.04975 * solTime, 360.0) - (0.000150 + 0.0000033 * solTime) * sqr(solTime), 360.0)
        )
        val l = radians(modulus(279.69668 + modulus(36000.76892 * solTime, 360.0) + 0.0003025 * sqr(solTime), 360.0))
        val e = 0.01675104 - (0.0000418 + 0.000000126 * solTime) * solTime
        val c = radians(
            (1.919460 - (0.004789 + 0.000014 * solTime) * solTime) * sin(m) +
                (0.020094 - 0.000100 * solTime) * sin(2 * m) + 0.000293 * sin(3 * m)
        )
        val o = radians(modulus(259.18 - 1934.142 * solTime, 360.0))
        val lsa = modulus(l + c - radians(0.00569 - 0.00479 * sin(o)), Satellite.TWO_PI)
        val nu = modulus(m + c, Satellite.TWO_PI)
        var r = 1.0000002 * (1.0 - sqr(e)) / (1.0 + e * cos(nu))
        val eps = radians(
            23.452294 - (0.0130125 + (0.00000164 - 0.000000503 * solTime) * solTime) * solTime + 0.00256 * cos(o)
        )
        r *= ASTRONOMICAL_UNIT

        return Vector4(r, r * cos(lsa), r * sin(lsa) * cos(eps), r * sin(lsa) * sin(eps))
    }

    /**
     * Provides the difference between UT (approximately the same as UTC) and ET (now referred to as TDT). Based on a
     * least squares fit of data from 1950 to 1991.
     */
    private fun deltaEt(year: Double): Double =
        26.465 + 0.747622 * (year - 1950) + 1.886913 * sin(Satellite.TWO_PI * (year - 1975) / 33)

    /** Returns angle in radians from argument in degrees. */
    private fun radians(degrees: Double): Double = degrees * Satellite.DEG2RAD

    protected fun calculatePhase(xlt: Double, xnode: Double, omgadf: Double) {
        /* Phase in radians */
        var phaseValue = xlt - xnode - omgadf + Satellite.TWO_PI
        if (phaseValue < 0.0) {
            phaseValue += Satellite.TWO_PI
        }
        satPos!!.phase = mod2PI(phaseValue)
    }

    protected fun calculatePositionAndVelocity(rk: Double, uk: Double, xnodek: Double, xinck: Double, rdotk: Double, rfdotk: Double) {
        /* Orientation vectors */
        val sinuk = sin(uk)
        val cosuk = cos(uk)
        val sinik = sin(xinck)
        val cosik = cos(xinck)
        val sinnok = sin(xnodek)
        val cosnok = cos(xnodek)
        val xmx = -sinnok * cosik
        val xmy = cosnok * cosik
        val ux = xmx * sinuk + cosnok * cosuk
        val uy = xmy * sinuk + sinnok * cosuk
        val uz = sinik * sinuk
        val vx = xmx * cosuk - cosnok * sinuk
        val vy = xmy * cosuk - sinnok * sinuk
        val vz = sinik * cosuk

        /* Position and velocity */
        position.setXYZ(ux, uy, uz)
        position.multiply(rk)
        velocity.x = rdotk * ux + rfdotk * vx
        velocity.y = rdotk * uy + rfdotk * vy
        velocity.z = rdotk * uz + rfdotk * vz
    }

    internal class Vector4(
        var w: Double = 0.0,
        var x: Double = 0.0,
        var y: Double = 0.0,
        var z: Double = 0.0
    ) : Serializable {

        override fun toString(): String = "w: $w, x: $x, y: $y, z: $z"

        fun multiply(multiplier: Double) {
            x *= multiplier
            y *= multiplier
            z *= multiplier
        }

        fun setXYZ(xValue: Double, yValue: Double, zValue: Double) {
            x = xValue
            y = yValue
            z = zValue
        }

        fun subtract(vector: Vector4): Vector4 = Vector4(w - vector.w, x - vector.x, y - vector.y, z - vector.z)

        companion object {
            private const val serialVersionUID = -8804649332186066551L

            fun scalarMultiply(vector: Vector4, multiplier: Double): Vector4 =
                Vector4(vector.w * abs(multiplier), vector.x * multiplier, vector.y * multiplier, vector.z * multiplier)

            /** Calculates the angle between vectors v1 and v2. */
            fun angle(v1: Vector4, v2: Vector4): Double {
                magnitude(v1)
                magnitude(v2)
                return acos(dot(v1, v2) / (v1.w * v2.w))
            }

            /** Subtracts vector v2 from v1. */
            fun subtract(v1: Vector4, v2: Vector4): Vector4 {
                val v3 = Vector4()
                v3.x = v1.x - v2.x
                v3.y = v1.y - v2.y
                v3.z = v1.z - v2.z
                magnitude(v3)
                return v3
            }
        }
    }

    internal companion object {
        private const val serialVersionUID = 1156988980297227860L
        private const val MINS_PER_DAY = 1.44E3
        private const val PI_OVER_TWO = Math.PI / 2.0
        private const val SECS_PER_DAY = 8.6400E4
        private const val FLATTENING_FACTOR = 3.35281066474748E-3

        const val CK4 = 6.209887E-7
        const val EARTH_GRAVITATIONAL_CONSTANT = 3.986008E5
        const val S = 1.012229
        const val QOMS2T = 1.880279E-09
        const val EARTH_ROTATIONS_PER_SIDERIAL_DAY = 1.00273790934
        const val EARTH_ROTATIONS_RADIANS_PER_SIDERIAL_DAY = EARTH_ROTATIONS_PER_SIDERIAL_DAY * Satellite.TWO_PI
        const val RHO = 1.5696615E-1
        const val MFACTOR = 7.292115E-5
        const val SOLAR_RADIUS_KM = 6.96000E5
        const val ASTRONOMICAL_UNIT = 1.49597870691E8
        const val SPEED_OF_LIGHT = 2.99792458E8
        const val PERIGEE_156_KM = 156.0

        /** WGS 84 Earth radius km */
        const val EARTH_RADIUS = 6.378137E3

        /** Solar radius - km (IAU 76) */
        const val SOLAR_RADIUS = 6.96000E5

        /** The time zone in which we do all the calculations. */
        val TZ: TimeZone = TimeZone.getTimeZone("UTC:UTC")

        /**
         * Calculates the Julian Day of the Year: the Julian Date of day 0.0 of [theYear].
         *
         * Astronomical Formulae for Calculators, Jean Meeus, pages 23-25.
         */
        fun julianDateOfYear(theYear: Double): Double {
            val aYear = theYear - 1
            var i = floor(aYear / 100).toLong()
            val a = i
            i = a / 4
            val b = 2 - a + i
            i = floor(365.25 * aYear).toLong()
            i = (i + 30.6001 * 14).toLong() // Java compound assignment narrows the double sum back to long
            return i + 1720994.5 + b
        }

        /**
         * Returns the Julian Date of an epoch specified in the format used in the NORAD two-line element sets. Valid
         * for 1957 through 2056.
         */
        private fun juliandDateOfEpoch(epoch: Double): Double {
            /* Modification to support Y2K */
            /* Valid 1957 through 2056 */
            var year = floor(epoch * 1E-3)
            val day = (epoch * 1E-3 - year) * 1000.0

            year = if (year < 57) year + 2000 else year + 1900

            return julianDateOfYear(year) + day
        }

        /** Returns the number of days since 31Dec79 00:00:00 UTC (daynum 0). */
        private fun calcCurrentDaynum(date: Date): Double {
            val now = date.time
            val sgp4Epoch = Calendar.getInstance(TZ)
            sgp4Epoch.clear()
            sgp4Epoch.set(1979, 11, 31, 0, 0, 0)
            val then = sgp4Epoch.timeInMillis
            val millis = now - then
            return millis / 1000.0 / 60.0 / 60.0 / 24.0
        }

        fun sqr(arg: Double): Double = arg * arg

        /** Calculates scalar magnitude of a vector4 argument. */
        internal fun magnitude(v: Vector4) {
            v.w = sqrt(sqr(v.x) + sqr(v.y) + sqr(v.z))
        }

        /** Multiplies the vector by the scalar k. */
        private fun scaleVector(k: Double, v: Vector4) {
            v.multiply(k)
            magnitude(v)
        }

        /** Returns arg1 mod arg2. */
        private fun modulus(arg1: Double, arg2: Double): Double {
            var returnValue = arg1
            val i = floor(returnValue / arg2).toInt()
            returnValue -= i * arg2
            if (returnValue < 0.0) {
                returnValue += arg2
            }
            return returnValue
        }

        /** Returns fractional part of double argument. */
        private fun frac(arg: Double): Double = arg - floor(arg)

        /** Reference: The 1992 Astronomical Almanac, page B6. */
        private fun thetaGJD(theJD: Double): Double {
            val ut = frac(theJD + 0.5)
            val aJD = theJD - ut
            val tu = (aJD - 2451545.0) / 36525.0
            var gmst = 24110.54841 + tu * (8640184.812866 + tu * (0.093104 - tu * 6.2E-6))
            gmst = modulus(gmst + SECS_PER_DAY * EARTH_ROTATIONS_PER_SIDERIAL_DAY * ut, SECS_PER_DAY)
            return Satellite.TWO_PI * gmst / SECS_PER_DAY
        }

        /** Calculates the dot product of two vectors. */
        internal fun dot(v1: Vector4, v2: Vector4): Double = v1.x * v2.x + v1.y * v2.y + v1.z * v2.z

        /** Returns mod 2PI of the argument. */
        fun mod2PI(testValue: Double): Double {
            var retVal = testValue
            val i = (retVal / Satellite.TWO_PI).toInt()
            retVal -= i * Satellite.TWO_PI
            if (retVal < 0.0) {
                retVal += Satellite.TWO_PI
            }
            return retVal
        }

        /** Converts the satellite's position and velocity vectors from normalized values to km and km/sec. */
        private fun convertSatState(pos: Vector4, vel: Vector4) {
            scaleVector(Satellite.EARTH_RADIUS_KM, pos)
            scaleVector(Satellite.EARTH_RADIUS_KM * MINS_PER_DAY / SECS_PER_DAY, vel)
        }

        private fun calculateUserPosVel(time: Double, gsPos: GroundStationPosition, obsPos: Vector4, obsVel: Vector4) {
            gsPos.theta = mod2PI(thetaGJD(time) + Satellite.DEG2RAD * gsPos.longitude)
            val c = invert(sqrt(1.0 + FLATTENING_FACTOR * (FLATTENING_FACTOR - 2) * sqr(sin(Satellite.DEG2RAD * gsPos.latitude))))
            val sq = sqr(1.0 - FLATTENING_FACTOR) * c
            val achcp = (Satellite.EARTH_RADIUS_KM * c + (gsPos.heightAMSL / 1000.0)) * cos(Satellite.DEG2RAD * gsPos.latitude)
            obsPos.setXYZ(
                achcp * cos(gsPos.theta), achcp * sin(gsPos.theta),
                (Satellite.EARTH_RADIUS_KM * sq + (gsPos.heightAMSL / 1000.0)) * sin(Satellite.DEG2RAD * gsPos.latitude)
            )
            obsVel.setXYZ(-MFACTOR * obsPos.y, MFACTOR * obsPos.x, 0.0)
            magnitude(obsPos)
            magnitude(obsVel)
        }

        fun invert(value: Double): Double = 1.0 / value

        /** Solves Kepler's Equation. [temp] is the array of temporary values passed around the orbit calculation. */
        fun converge(temp: DoubleArray, axn: Double, ayn: Double, capu: Double) {
            var converged = false
            var i = 0
            do {
                temp[7] = sin(temp[2])
                temp[8] = cos(temp[2])
                temp[3] = axn * temp[7]
                temp[4] = ayn * temp[8]
                temp[5] = axn * temp[8]
                temp[6] = ayn * temp[7]
                val epw = (capu - temp[4] + temp[3] - temp[2]) / (1.0 - temp[5] - temp[6]) + temp[2]
                if (abs(epw - temp[2]) <= Satellite.EPSILON) {
                    converged = true
                } else {
                    temp[2] = epw
                }
            } while (i++ < 10 && !converged)
        }
    }
}
