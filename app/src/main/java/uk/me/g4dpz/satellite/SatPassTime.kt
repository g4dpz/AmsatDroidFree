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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SatPassTime : Serializable {

    // Field names and types are part of the serialized form of saved passes; do not rename.
    // The getters hand out copies so callers cannot mutate the stored dates.
    var startTime: Date = Date(0)
        get() = Date(field.time)
        private set
    var endTime: Date = Date(0)
        get() = Date(field.time)
        private set
    var tca: Date = Date(0)
        get() = Date(field.time)
    var polePassed: String? = null
        private set
    private var aos: Int = 0
    private var los: Int = 0
    var maxEl: Double = 0.0
        private set

    constructor()

    constructor(startTime: Date, endTime: Date, tca: Date, polePassed: String?, aosAzimuth: Int, losAzimuth: Int, maxEl: Double) {
        this.startTime = Date(startTime.time)
        this.endTime = Date(endTime.time)
        this.polePassed = polePassed
        this.aos = aosAzimuth
        this.los = losAzimuth
        this.maxEl = maxEl
        this.tca = Date(tca.time)
    }

    /** The time of closest approach defaults to the mid point of the pass. */
    constructor(startTime: Date, endTime: Date, polePassed: String?, aos: Int, los: Int, maxEl: Double) :
        this(startTime, endTime, Date((startTime.time + endTime.time) / 2), polePassed, aos, los, maxEl)

    val aosAzimuth: Int get() = aos

    val losAzimuth: Int get() = los

    override fun toString(): String = passDateTimeString() + NEW_LINE + passInfoString()

    fun passDateTimeString(): String {
        val duration = (endTime.time - startTime.time) / 60000.0
        return "Date: " + DATE_FORMAT.format(startTime) + NEW_LINE + "Start Time: " + TIME_FORMAT.format(startTime) +
            " loc" + NEW_LINE + String.format(Locale.ENGLISH, "Duration: %4.1f min.", duration)
    }

    fun passInfoString(): String =
        "AOS Azimuth: " + aos + DEG_NL + String.format(Locale.ENGLISH, "Max Elevation: %4.1f\u00B0\n", maxEl) + "LOS Azimuth: " + los + "\u00B0"

    companion object {
        private const val serialVersionUID = -6408342316986801301L
        private const val NEW_LINE = "\n"
        private const val DEG_NL = "\u00B0\n"
        private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.ENGLISH)
        private val DATE_FORMAT = SimpleDateFormat("MM dd, yyyy", Locale.ENGLISH)
    }
}
