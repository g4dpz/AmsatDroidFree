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

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Predicts passes of a satellite over a ground station.
 *
 * @throws IllegalArgumentException bad argument passed in
 * @throws SatNotFoundException the satellite can not be created or will never be seen
 * @throws InvalidTleException the TLE is invalid
 */
class PassPredictor @Throws(IllegalArgumentException::class, InvalidTleException::class, SatNotFoundException::class) constructor(
    theTLE: TLE?,
    theQTH: GroundStationPosition?
) {

    private var newTLE = true
    private val tle: TLE
    private val qth: GroundStationPosition
    private var sat: Satellite? = null
    private var windBackTime = false
    private val meanMotion: Double
    var iterationCount: Int = 0
        private set
    private var tca: Date? = null

    init {
        requireNotNull(theTLE) { "TLE has not been set" }
        requireNotNull(theQTH) { "QTH has not been set" }
        tle = theTLE
        qth = theQTH
        newTLE = true
        validateData()
        meanMotion = theTLE.meanmo
    }

    /**
     * Gets the downlink frequency corrected for doppler.
     *
     * @param freq the original frequency in Hz
     * @return the doppler corrected frequency in Hz
     */
    @Throws(InvalidTleException::class, SatNotFoundException::class)
    fun getDownlinkFreq(freq: Long, date: Date): Long {
        validateData()
        val rangeRate = getSatPos(utcCalendar(date).time).rangeRate
        return (freq.toDouble() * (SPEED_OF_LIGHT - rangeRate * 1000.0) / SPEED_OF_LIGHT).toLong()
    }

    @Throws(InvalidTleException::class, SatNotFoundException::class)
    fun getUplinkFreq(freq: Long, date: Date): Long {
        validateData()
        val rangeRate = getSatPos(utcCalendar(date).time).rangeRate
        return (freq.toDouble() * (SPEED_OF_LIGHT + rangeRate * 1000.0) / SPEED_OF_LIGHT).toLong()
    }

    private fun utcCalendar(date: Date): Calendar {
        val cal = Calendar.getInstance(TZ)
        cal.clear()
        cal.timeInMillis = date.time
        return cal
    }

    @Throws(InvalidTleException::class, SatNotFoundException::class)
    private fun getSatPos(time: Date): SatPos {
        iterationCount++
        return sat!!.getPosition(qth, time)
    }

    @JvmOverloads
    @Throws(InvalidTleException::class, SatNotFoundException::class)
    fun nextSatPass(date: Date, windBack: Boolean = false): SatPassTime {
        var aosAzimuth = 0
        var losAzimuth = 0
        var maxElevation = 0.0
        var elevation: Double

        validateData()

        var polePassed = DEADSPOT_NONE

        // get the current position
        val cal = utcCalendar(date)

        // wind back time 1/4 of an orbit
        if (windBack) {
            cal.add(Calendar.MINUTE, (-24.0 * 60.0 / meanMotion / 4.0).toInt())
        }

        var satPos = getSatPos(cal.time)
        var prevPos = satPos

        // The search is bounded: a satellite that never crosses the horizon from this station (geostationary and
        // similar) would otherwise keep these loops stepping forward for ever. willBeSeen() cannot catch that case,
        // because such a satellite may well be "seen" (it is simply always up, or always down).
        val giveUpAtMillis = cal.timeInMillis + MAX_SEARCH_MILLIS

        // test for the elevation being above the horizon
        if (satPos.elevation > 0.0) {
            // move time forward in 60 second intervals until the sat goes below the horizon
            do {
                satPos = getPosition(cal, 60)
                checkSearchBound(cal, giveUpAtMillis)
            } while (satPos.elevation > 0.0)

            // move time forward 3/4 orbit
            cal.add(Calendar.MINUTE, threeQuarterOrbitMinutes())
        }

        // now find the next time it comes above the horizon
        do {
            satPos = getPosition(cal, 60)
            checkSearchBound(cal, giveUpAtMillis)
            val now = cal.time
            elevation = satPos.elevation
            if (elevation > maxElevation) {
                maxElevation = elevation
                tca = now
            }
        } while (satPos.elevation < 0.0)

        // refine it to 5 seconds
        cal.add(Calendar.SECOND, -60)
        do {
            satPos = getPosition(cal, 5)
            val now = cal.time
            elevation = satPos.elevation
            if (elevation > maxElevation) {
                maxElevation = elevation
                tca = now
            }
            prevPos = satPos
        } while (satPos.elevation < 0.0)

        val startDate = satPos.time
        aosAzimuth = ((satPos.azimuth / (2.0 * Math.PI)) * 360.0).toInt()

        // now find when it goes below
        do {
            satPos = getPosition(cal, 30)
            checkSearchBound(cal, giveUpAtMillis)
            val now = cal.time

            val currPolePassed = getPolePassed(prevPos, satPos)
            if (currPolePassed != DEADSPOT_NONE) {
                polePassed = currPolePassed
            }

            elevation = satPos.elevation
            if (elevation > maxElevation) {
                maxElevation = elevation
                tca = now
            }
            prevPos = satPos
        } while (satPos.elevation > 0.0)

        newTLE = true
        validateData()

        // refine it to 5 seconds
        cal.add(Calendar.SECOND, -30)
        do {
            satPos = getPosition(cal, 5)
            val now = cal.time
            elevation = satPos.elevation
            if (elevation > maxElevation) {
                maxElevation = elevation
                tca = now
            }
        } while (satPos.elevation > 0.0)

        val endDate = satPos.time
        losAzimuth = ((satPos.azimuth / (2.0 * Math.PI)) * 360.0).toInt()

        return SatPassTime(
            startDate, endDate, tca!!, polePassed, aosAzimuth, losAzimuth,
            (maxElevation / (2.0 * Math.PI)) * 360.0
        )
    }

    @Throws(InvalidTleException::class, SatNotFoundException::class)
    private fun getPosition(cal: Calendar, offSet: Int): SatPos {
        cal.add(Calendar.SECOND, offSet)
        return getSatPos(cal.time)
    }

    /** Stops a horizon search that has gone on for [MAX_SEARCH_MILLIS] of satellite time without a crossing. */
    @Throws(SatNotFoundException::class)
    private fun checkSearchBound(cal: Calendar, giveUpAtMillis: Long) {
        if (cal.timeInMillis > giveUpAtMillis) {
            throw SatNotFoundException(
                "No horizon crossing within $MAX_SEARCH_DAYS days: the satellite does not rise or set here"
            )
        }
    }

    /**
     * Gets a list of passes.
     *
     * @param start the time to start looking from
     * @param hoursAhead how many hours ahead to look for the first AOS
     * @param windBack whether to wind back 1/4 of an orbit first, to catch a pass already in progress
     */
    @Throws(InvalidTleException::class, SatNotFoundException::class)
    fun getPasses(start: Date, hoursAhead: Int, windBack: Boolean): List<SatPassTime> {
        iterationCount = 0
        windBackTime = windBack

        val passes = ArrayList<SatPassTime>()
        var trackStartDate = start
        val trackEndDate = Date(start.time + (hoursAhead * 60L * 60L * 1000L))

        var lastAOS: Date
        var count = 0
        do {
            if (count > 0) {
                windBackTime = false
            }
            val pass = nextSatPass(trackStartDate, windBackTime)
            lastAOS = pass.startTime
            passes.add(pass)
            trackStartDate = Date(pass.endTime.time + (threeQuarterOrbitMinutes() * 60L * 1000L))
            count++
        } while (lastAOS < trackEndDate)

        return passes
    }

    @Throws(InvalidTleException::class, SatNotFoundException::class)
    private fun validateData() {
        if (newTLE) {
            val created = SatelliteFactory.createSatellite(tle)
            sat = created
            if (!created.willBeSeen(qth)) {
                throw SatNotFoundException("Satellite will never appear above the horizon")
            }
            newTLE = false
        }
    }

    /** @return time in minutes for 3/4 of an orbit */
    private fun threeQuarterOrbitMinutes(): Int = (24.0 * 60.0 / tle.meanmo * 0.75).toInt()

    private fun getPolePassed(prevPos: SatPos, satPos: SatPos): String {
        var polePassed = DEADSPOT_NONE
        val az1 = prevPos.azimuth / TWOPI * 360.0
        val az2 = satPos.azimuth / TWOPI * 360.0

        if (az1 > az2) {
            // we may be moving from 350 or greater through north
            if (az1 > 350 && az2 < 10) {
                polePassed = NORTH
            } else if (az1 > 180 && az2 < 180) {
                // we may be moving from 190 or greater through south
                polePassed = SOUTH
            }
        } else {
            // we may be moving from 10 or less through north
            if (az1 < 10 && az2 > 350) {
                polePassed = NORTH
            } else if (az1 < 180 && az2 > 180) {
                // we may be moving from 170 or more through south
                polePassed = SOUTH
            }
        }
        return polePassed
    }

    /**
     * Calculates positions of the satellite for a given point in time, time range and step increment.
     *
     * @return list of SatPos
     */
    @Throws(InvalidTleException::class, SatNotFoundException::class)
    fun getPositions(referenceDate: Date, incrementSeconds: Int, minutesBefore: Int, minutesAfter: Int): List<SatPos> {
        var trackDate = Date(referenceDate.time - (minutesBefore * 60L * 1000L))
        val endDateDate = Date(referenceDate.time + (minutesAfter * 60L * 1000L))

        val positions = ArrayList<SatPos>()
        while (trackDate.before(endDateDate)) {
            positions.add(getSatPos(trackDate))
            trackDate = Date(trackDate.time + (incrementSeconds * 1000))
        }
        return positions
    }

    companion object {
        /**
         * How far ahead [nextSatPass] searches for a horizon crossing before giving up with [SatNotFoundException].
         * Every satellite that crosses the horizon at all does so well inside this (a day or so at most, even for
         * highly elliptical orbits); the bound only matters for satellites that stay put in the sky.
         */
        const val MAX_SEARCH_DAYS = 10L
        const val MAX_SEARCH_MILLIS: Long = MAX_SEARCH_DAYS * 24 * 60 * 60 * 1000

        private const val UTC = "UTC"
        private const val SOUTH = "south"
        private const val NORTH = "north"
        private const val SPEED_OF_LIGHT = 2.99792458E8
        private const val TWOPI = Math.PI * 2.0
        private const val DEADSPOT_NONE = "none"

        /** The time zone in which we do all the calculations. */
        private val TZ: TimeZone = TimeZone.getTimeZone(UTC)
    }
}
