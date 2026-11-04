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

import java.text.NumberFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * A satellite position. Angles are held internally in radians.
 */
class SatPos() {

    var azimuth: Double = 0.0
    var elevation: Double = 0.0
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var time: Date = Date(0)
        get() = Date(field.time)
        set(value) {
            field = Date(value.time)
        }
    var range: Double = 0.0
    var rangeRate: Double = 0.0
    var phase: Double = 0.0
    var altitude: Double = 0.0
    var theta: Double = 0.0
    internal var eclipseDepth: Double = 0.0
    internal var isEclipsed: Boolean = false
    var isAboveHorizon: Boolean = false

    constructor(azimuth: Double, elevation: Double, theTime: Date) : this() {
        this.azimuth = azimuth
        this.elevation = elevation
        this.time = Date(theTime.time)
    }

    /** @return a pretty printed version of the Satellite Position */
    override fun toString(): String =
        "Azimuth:    " + azimuth / (Math.PI * 2.0) * 360 + DEG_CR + "Elevation:  " + elevation / (Math.PI * 2.0) * 360 +
            DEG_CR + "Latitude:   " + latitude / (Math.PI * 2.0) * 360 + DEG_CR + "Longitude:  " +
            longitude / (Math.PI * 2.0) * 360 + DEG_CR +
            "Date:       " + time + NL + "Range:        " + range + " km.\n" + "Range rate:   " + rangeRate + " m/S.\n" +
            "Phase:        " + phase + " /(256)\n" + "Altitude:     " + altitude + " km\n" + "Theta:        " + theta +
            " rad/sec\n" + "Eclipsed:     " + isEclipsed + NL + "Eclipse depth:" + eclipseDepth + " radians\n"

    fun toShortString(): String {
        val numberFormat = NumberFormat.getNumberInstance()
        numberFormat.maximumFractionDigits = 0
        var returnString = "Elevation: " + numberFormat.format(elevation / (Math.PI * 2.0) * 360) + DEG_CR +
            "Azimuth: " + numberFormat.format(azimuth / (Math.PI * 2.0) * 360) + DEG_CR
        numberFormat.maximumFractionDigits = 2
        returnString += "Latitude: " + numberFormat.format(latitude / (Math.PI * 2.0) * 360) + DEG_CR +
            "Longitude: " + numberFormat.format(longitude / (Math.PI * 2.0) * 360) + DEG_CR
        numberFormat.maximumFractionDigits = 0
        returnString += "Range: " + numberFormat.format(range) + " Km"
        return returnString
    }

    fun copy(other: SatPos) {
        isAboveHorizon = other.isAboveHorizon
        altitude = other.altitude
        azimuth = other.azimuth
        elevation = other.elevation
        latitude = other.latitude
        longitude = other.longitude
        phase = other.phase
        range = other.range
        rangeRate = other.rangeRate
        theta = other.theta
        time = other.time
        eclipseDepth = other.eclipseDepth
        isEclipsed = other.isEclipsed
    }

    /** The footprint range circle as 360 pairs of latitude and longitude, in degrees. */
    val rangeCircle: Array<DoubleArray>
        get() = calculateRangeCirclePoints(this)

    private companion object {
        const val NL = "\n"
        const val DEG_CR = "\u00B0\n"

        /** WGS 84 Earth radius km */
        const val EARTH_RADIUS = 6.378137E3
        const val R0 = 6378.16

        /** Calculates the footprint range circle. */
        fun calculateRangeCirclePoints(pos: SatPos): Array<DoubleArray> {
            val dia = (12756.33 * acos(EARTH_RADIUS / (EARTH_RADIUS + pos.altitude))).toInt()
            val latitude = pos.latitude
            val longitude = pos.longitude
            val beta = (0.5 * dia) / R0
            val result = Array(360) { DoubleArray(2) }

            for (azi in 0 until 360) {
                val azimuth = azi / 360.0 * 2.0 * Math.PI
                var rangelat = asin(sin(latitude) * cos(beta) + cos(azimuth) * sin(beta) * cos(latitude))
                val num = cos(beta) - (sin(latitude) * sin(rangelat))
                val den = cos(latitude) * cos(rangelat)
                var rangelong: Double
                if (azi == 0 && beta > ((Math.PI / 2.0) - latitude)) {
                    rangelong = longitude + Math.PI
                } else if (azi == 180 && beta > ((Math.PI / 2.0) - latitude)) {
                    rangelong = longitude + Math.PI
                } else if (abs(num / den) > 1.0) {
                    rangelong = longitude
                } else {
                    rangelong = if ((180 - azi) >= 0) longitude - acos(num / den) else longitude + acos(num / den)
                }

                while (rangelong < 0.0) {
                    rangelong += Math.PI * 2.0
                }
                while (rangelong > Math.PI * 2.0) {
                    rangelong -= Math.PI * 2.0
                }

                rangelat = (rangelat / (2.0 * Math.PI)) * 360.0
                rangelong = (rangelong / (2.0 * Math.PI)) * 360.0

                result[azi][0] = rangelat
                result[azi][1] = rangelong
            }
            return result
        }
    }
}
