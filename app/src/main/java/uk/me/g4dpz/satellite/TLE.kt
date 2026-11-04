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

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Serializable
import java.util.Date
import kotlin.math.cos
import kotlin.math.pow

/**
 * Two Line Elements for a satellite.
 *
 * Note: field names and types were part of the serialized form of the old saved element files. The app no longer
 * reads or writes those files, but the names are kept so existing code and tests are unaffected.
 */
class TLE : Serializable {

    var catnum: Int = 0
        private set
    var name: String = ""
        private set
    var setnum: Int = 0
        private set
    var year: Int = 0
        private set
    var refepoch: Double = 0.0
        private set
    var incl: Double = 0.0
        private set
    var raan: Double = 0.0
        private set
    var eccn: Double = 0.0
        private set
    var argper: Double = 0.0
        private set
    var meanan: Double = 0.0
        private set
    var meanmo: Double = 0.0
        private set
    var drag: Double = 0.0
        private set
    var nddot6: Double = 0.0
        private set
    var bstar: Double = 0.0
        private set
    var orbitnum: Int = 0
        private set
    var epoch: Double = 0.0
        private set
    var xndt2o: Double = 0.0
        private set
    var xincl: Double = 0.0
        private set
    var xnodeo: Double = 0.0
        private set
    var eo: Double = 0.0
        private set
    var omegao: Double = 0.0
        private set
    var xmo: Double = 0.0
        private set
    var xno: Double = 0.0
        private set
    private var deepspace: Boolean = false
    private var createddate: Date? = null

    /** True if this orbit needs the SDP4 (deep space) model. */
    val isDeepspace: Boolean get() = deepspace

    /** Copy constructor. */
    constructor(tle: TLE) {
        catnum = tle.catnum
        name = tle.name
        setnum = tle.setnum
        year = tle.year
        refepoch = tle.refepoch
        incl = tle.incl
        raan = tle.raan
        eccn = tle.eccn
        argper = tle.argper
        meanan = tle.meanan
        meanmo = tle.meanmo
        drag = tle.drag
        nddot6 = tle.nddot6
        bstar = tle.bstar
        orbitnum = tle.orbitnum
        epoch = tle.epoch
        xndt2o = tle.xndt2o
        xincl = tle.xincl
        xnodeo = tle.xnodeo
        eo = tle.eo
        omegao = tle.omegao
        xmo = tle.xmo
        xno = tle.xno
        deepspace = tle.deepspace
        createddate = tle.createddate
    }

    /** Builds a TLE from numeric elements; use [fromElements]. */
    private constructor(e: OrbitalElements) {
        catnum = e.catnum
        name = e.name.trim()
        setnum = e.elementSetNo
        year = e.epochYear % 100
        refepoch = e.epochDay
        incl = e.inclination
        raan = e.raan
        eccn = e.eccentricity
        argper = e.argPerigee
        meanan = e.meanAnomaly
        meanmo = e.meanMotion
        drag = e.meanMotionDot
        nddot6 = e.meanMotionDdot
        bstar = e.bstar
        orbitnum = e.revAtEpoch

        /* reassign the values to those which get used in calculations (same as the text constructor) */
        epoch = (1000.0 * year) + refepoch
        xndt2o = drag
        xincl = incl * DEG2RAD
        xnodeo = raan * DEG2RAD
        eo = eccn
        omegao = argper * DEG2RAD
        xmo = meanan * DEG2RAD
        xno = meanmo

        preProcessTLESet()
    }

    /**
     * @param tle the three line elements: name, line 1, line 2
     * @throws IllegalArgumentException if there was something wrong with the TLE
     */
    @Throws(IllegalArgumentException::class)
    constructor(tle: Array<String?>?) {
        requireNotNull(tle) { "TLE was null" }
        require(tle.size == THREELINES) { "TLE had ${tle.size} elements" }
        for ((lineCount, line) in tle.withIndex()) {
            testArguments(lineCount, line)
        }
        val name0 = tle[0]!!
        val l1 = tle[1]!!
        val l2 = tle[2]!!

        catnum = l1.substring(2, 7).trim().toInt()
        name = name0.trim()
        setnum = l1.substring(64, 68).trim().toInt()
        year = l1.substring(18, 20).trim().toInt()
        refepoch = l1.substring(20, 32).toDouble()
        incl = l2.substring(8, 16).toDouble()
        raan = l2.substring(17, 25).toDouble()
        eccn = 1.0e-07 * l2.substring(26, 33).toDouble()
        argper = l2.substring(34, 42).toDouble()
        meanan = l2.substring(43, 51).toDouble()
        meanmo = l2.substring(52, 63).toDouble()
        drag = l1.substring(33, 43).toDouble()
        var tempnum = 1.0e-5 * l1.substring(44, 50).toDouble()
        nddot6 = tempnum / 10.0.pow(l1.substring(51, 52).toDouble())
        tempnum = 1.0e-5 * l1.substring(53, 59).toDouble()
        bstar = tempnum / 10.0.pow(l1.substring(60, 61).toDouble())
        orbitnum = l2.substring(63, 68).trim().toInt()

        /* reassign the values to those which get used in calculations */
        epoch = (1000.0 * year) + refepoch
        xndt2o = drag
        xincl = incl * DEG2RAD
        xnodeo = raan * DEG2RAD
        eo = eccn
        omegao = argper * DEG2RAD
        xmo = meanan * DEG2RAD
        xno = meanmo

        /* Preprocess tle set */
        preProcessTLESet()
    }

    @Synchronized
    private fun preProcessTLESet() {
        var temp = TWO_PI / MINS_PERDAY / MINS_PERDAY
        xno = xno * temp * MINS_PERDAY
        xndt2o *= temp
        var dd1 = XKE / xno
        val a1 = dd1.pow(TWO_THIRDS)
        val r1 = cos(xincl)
        dd1 = 1.0 - eo * eo
        temp = CK2 * 1.5 * (r1 * r1 * 3.0 - 1.0) / dd1.pow(1.5)
        val del1 = temp / (a1 * a1)
        val ao = a1 * (1.0 - del1 * (TWO_THIRDS * .5 + del1 * (del1 * 1.654320987654321 + 1.0)))
        val delo = temp / (ao * ao)
        val xnodp = xno / (delo + 1.0)

        /* Select a deep-space/near-earth ephemeris */
        deepspace = TWO_PI / xnodp / MINS_PERDAY >= 0.15625
    }

    @Throws(IllegalArgumentException::class)
    private fun testArguments(lineCount: Int, line: String?) {
        requireNotNull(line) { createIllegalArgumentMessage(lineCount, "was null") }
        require(line.isNotEmpty()) { createIllegalArgumentMessage(lineCount, "was zero length") }
    }

    private fun createIllegalArgumentMessage(lineCount: Int, problem: String): String = "TLE line[$lineCount] $problem"

    fun getCreateddate(): Date = Date(createddate!!.time)

    fun setCreateddate(createddate: Date) {
        this.createddate = Date(createddate.time)
    }

    override fun toString(): String = name

    companion object {
        private const val serialVersionUID = 716922882884628016L
        private const val THREELINES = 3
        private const val DEG2RAD = 1.745329251994330E-2
        private const val TWO_PI = Math.PI * 2.0
        private const val MINS_PERDAY = 1.44E3
        private const val XKE = 7.43669161E-2
        private const val TWO_THIRDS = 2.0 / 3.0
        private const val CK2 = 5.413079E-4

        /**
         * Builds a TLE from orbital elements, for example ones read from Celestrak JSON. This is how satellites with
         * catalogue numbers above 99999 (which TLE text cannot hold) reach the prediction code.
         *
         * @throws IllegalArgumentException if the elements are not valid
         */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromElements(elements: OrbitalElements): TLE {
            elements.validate()
            return TLE(elements)
        }

        /**
         * Reads a file of three-line element sets (name, line 1, line 2).
         *
         * @throws IOException if the stream cannot be read
         */
        @JvmStatic
        @Throws(IOException::class)
        fun importSat(fileIS: InputStream): List<TLE> {
            val importedSats = ArrayList<TLE>()
            val lines = arrayOfNulls<String>(THREELINES)
            var j = 0
            BufferedReader(InputStreamReader(fileIS)).useLines { sequence ->
                for (readString in sequence) {
                    lines[j] = readString
                    if (j == 2) {
                        j = 0
                        importedSats.add(TLE(lines))
                    } else {
                        j++
                    }
                }
            }
            return importedSats
        }
    }
}
