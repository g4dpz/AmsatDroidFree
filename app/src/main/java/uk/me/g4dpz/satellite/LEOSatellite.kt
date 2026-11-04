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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A Low Earth Orbit satellite, propagated with the SGP4 model.
 *
 * @author g4dpz
 */
class LEOSatellite(tle: TLE) : AbstractSatellite(tle), Serializable {

    private var aodp = 0.0
    private var aycof = 0.0
    private var c1 = 0.0
    private var c4 = 0.0
    private var c5 = 0.0
    private var cosio = 0.0
    private var d2 = 0.0
    private var d3 = 0.0
    private var d4 = 0.0
    private var delmo = 0.0
    private var omgcof = 0.0
    private var eta = 0.0
    private var omgdot = 0.0
    private var sinio = 0.0
    private var xnodp = 0.0
    private var sinmo = 0.0
    private var t2cof = 0.0
    private var t3cof = 0.0
    private var t4cof = 0.0
    private var t5cof = 0.0
    private var x1mth2 = 0.0
    private var x3thm1 = 0.0
    private var x7thm1 = 0.0
    private var xmcof = 0.0
    private var xmdot = 0.0
    private var xnodcf = 0.0
    private var xnodot = 0.0
    private var xlcof = 0.0
    private var sgp4Init = false
    private var sgp4Simple = false

    init {
        sgp4Init()
    }

    override fun calculateSGP4(tsince: Double) {
        val temp = DoubleArray(9)

        /* Initialization */
        if (!sgp4Init) {
            sgp4Init()
        }

        /* Update for secular gravity and atmospheric drag. */
        val xmdf = tle.xmo + xmdot * tsince
        val omgadf = tle.omegao + omgdot * tsince
        val xnoddf = tle.xnodeo + xnodot * tsince
        var omega = omgadf
        var xmp = xmdf
        val tsq = sqr(tsince)
        val xnode = xnoddf + xnodcf * tsq
        val bstar = tle.bstar
        var tempa = 1.0 - c1 * tsince
        var tempe = bstar * c4 * tsince
        var templ = t2cof * tsq

        if (!sgp4Simple) {
            val delomg = omgcof * tsince
            val delm = xmcof * ((1.0 + eta * cos(xmdf)).pow(3) - delmo)
            temp[0] = delomg + delm
            xmp = xmdf + temp[0]
            omega = omgadf - temp[0]
            val tcube = tsq * tsince
            val tfour = tsince * tcube
            tempa = tempa - d2 * tsq - d3 * tcube - d4 * tfour
            tempe = tempe + bstar * c5 * (sin(xmp) - sinmo)
            templ = templ + t3cof * tcube + tfour * (t4cof + tsince * t5cof)
        }

        val a = aodp * tempa.pow(2)
        val eo = tle.eo
        val e = eo - tempe
        val xl = xmp + omega + xnode + xnodp * templ
        val beta = sqrt(1.0 - e * e)
        val xn = Satellite.XKE / a.pow(1.5)

        /* Long period periodics */
        val axn = e * cos(omega)
        temp[0] = invert(a * sqr(beta))
        val xll = temp[0] * xlcof * axn
        val aynl = temp[0] * aycof
        val xlt = xl + xll
        val ayn = e * sin(omega) + aynl

        /* Solve Kepler's Equation */
        val capu = mod2PI(xlt - xnode)
        temp[2] = capu
        converge(temp, axn, ayn, capu)

        calculatePositionAndVelocity(temp, xnode, a, xn, axn, ayn)

        calculatePhase(xlt, xnode, omgadf)
    }

    private fun calculatePositionAndVelocity(temp: DoubleArray, xnode: Double, a: Double, xn: Double, axn: Double, ayn: Double) {
        val ecose = temp[5] + temp[6]
        val esine = temp[3] - temp[4]
        val elsq = sqr(axn) + sqr(ayn)
        temp[0] = 1.0 - elsq
        val pl = a * temp[0]
        val r = a * (1.0 - ecose)
        temp[1] = invert(r)
        val rdot = Satellite.XKE * sqrt(a) * esine * temp[1]
        val rfdot = Satellite.XKE * sqrt(pl) * temp[1]
        temp[2] = a * temp[1]
        val betal = sqrt(temp[0])
        temp[3] = invert(1.0 + betal)
        val cosu = temp[2] * (temp[8] - axn + ayn * esine * temp[3])
        val sinu = temp[2] * (temp[7] - ayn - axn * esine * temp[3])
        val u = atan2(sinu, cosu)
        val sin2u = 2.0 * sinu * cosu
        val cos2u = 2.0 * cosu * cosu - 1
        temp[0] = invert(pl)
        temp[1] = Satellite.CK2 * temp[0]
        temp[2] = temp[1] * temp[0]

        /* Update for short periodics */
        val rk = r * (1.0 - 1.5 * temp[2] * betal * x3thm1) + 0.5 * temp[1] * x1mth2 * cos2u
        val uk = u - 0.25 * temp[2] * x7thm1 * sin2u
        val xnodek = xnode + 1.5 * temp[2] * cosio * sin2u
        val xinck = tle.xincl + 1.5 * temp[2] * cosio * sinio * cos2u
        val rdotk = rdot - xn * temp[1] * x1mth2 * sin2u
        val rfdotk = rfdot + xn * temp[1] * (x1mth2 * cos2u + 1.5 * x3thm1)

        super.calculatePositionAndVelocity(rk, uk, xnodek, xinck, rdotk, rfdotk)
    }

    private fun sgp4Init() {
        /* Recover original mean motion (xnodp) and semimajor axis (aodp) from input elements. */
        val a1 = (Satellite.XKE / tle.xno).pow(Satellite.TWO_THIRDS)
        cosio = cos(tle.xincl)
        val theta2 = sqr(cosio)
        x3thm1 = 3.0 * theta2 - 1.0
        val eo = tle.eo
        val eosq = sqr(eo)
        val betao2 = 1.0 - eosq
        val betao = sqrt(betao2)
        val del1 = 1.5 * Satellite.CK2 * x3thm1 / (sqr(a1) * betao * betao2)
        val ao = a1 * (1.0 - del1 * (0.5 * Satellite.TWO_THIRDS + del1 * (1.0 + 134.0 / 81.0 * del1)))
        val delo = 1.5 * Satellite.CK2 * x3thm1 / (sqr(ao) * betao * betao2)
        xnodp = tle.xno / (1.0 + delo)
        aodp = ao / (1.0 - delo)

        /*
         * For perigee less than 220 kilometers, the "simple" flag is set and the equations are truncated to linear
         * variation in sqrt a and quadratic variation in mean anomaly. Also, the c3 term, the delta omega term, and
         * the delta m term are dropped.
         */
        sgp4Simple = (aodp * (1.0 - eo)) < (220 / Satellite.EARTH_RADIUS_KM + 1.0)

        /* For perigees below 156 km, the values of S and QOMS2T are altered. */
        setPerigee((aodp * (1.0 - eo) - 1.0) * Satellite.EARTH_RADIUS_KM)

        checkPerigee()

        val pinvsq = invert(sqr(aodp) * sqr(betao2))
        val tsi = invert(aodp - getS4())
        eta = aodp * eo * tsi
        val etasq = eta * eta
        val eeta = eo * eta
        val psisq = abs(1.0 - etasq)
        val coef = getQoms24() * tsi.pow(4)
        val coef1 = coef / psisq.pow(3.5)
        val bstar = tle.bstar
        val c2 = coef1 * xnodp * (
            aodp * (1.0 + 1.5 * etasq + eeta * (4.0 + etasq)) +
                0.75 * Satellite.CK2 * tsi / psisq * x3thm1 * (8.0 + 3.0 * etasq * (8.0 + etasq))
            )
        c1 = bstar * c2
        sinio = sin(tle.xincl)
        val a3ovk2 = -Satellite.J3_HARMONIC / Satellite.CK2
        val c3 = coef * tsi * a3ovk2 * xnodp * sinio / eo
        x1mth2 = 1.0 - theta2
        val omegao = tle.omegao
        c4 = 2 * xnodp * coef1 * aodp * betao2 * (
            eta * (2.0 + 0.5 * etasq) + eo * (0.5 + 2 * etasq) - 2 * Satellite.CK2 * tsi / (aodp * psisq) * (
                -3 * x3thm1 * (1.0 - 2 * eeta + etasq * (1.5 - 0.5 * eeta)) +
                    0.75 * x1mth2 * (2.0 * etasq - eeta * (1.0 + etasq)) * cos(2.0 * omegao)
                )
            )
        c5 = 2.0 * coef1 * aodp * betao2 * (1.0 + 2.75 * (etasq + eeta) + eeta * etasq)
        val theta4 = sqr(theta2)
        val temp1 = 3.0 * Satellite.CK2 * pinvsq * xnodp
        val temp2 = temp1 * Satellite.CK2 * pinvsq
        val temp3 = 1.25 * CK4 * pinvsq * pinvsq * xnodp
        xmdot = xnodp + 0.5 * temp1 * betao * x3thm1 + 0.0625 * temp2 * betao * (13.0 - 78.0 * theta2 + 137.0 * theta4)
        val x1m5th = 1.0 - 5.0 * theta2
        omgdot = -0.5 * temp1 * x1m5th + 0.0625 * temp2 * (7.0 - 114.0 * theta2 + 395.0 * theta4) +
            temp3 * (3.0 - 36.0 * theta2 + 49.0 * theta4)
        val xhdot1 = -temp1 * cosio
        xnodot = xhdot1 + (0.5 * temp2 * (4.0 - 19.0 * theta2) + 2.0 * temp3 * (3.0 - 7.0 * theta2)) * cosio
        omgcof = bstar * c3 * cos(omegao)
        xmcof = -Satellite.TWO_THIRDS * coef * bstar / eeta
        xnodcf = 3.5 * betao2 * xhdot1 * c1
        t2cof = 1.5 * c1
        xlcof = 0.125 * a3ovk2 * sinio * (3.0 + 5 * cosio) / (1.0 + cosio)
        aycof = 0.25 * a3ovk2 * sinio
        val xmo = tle.xmo
        delmo = (1.0 + eta * cos(xmo)).pow(3)
        sinmo = sin(xmo)
        x7thm1 = 7.0 * theta2 - 1

        if (!sgp4Simple) {
            val c1sq = sqr(c1)
            d2 = 4.0 * aodp * tsi * c1sq
            val temp = d2 * tsi * c1 / 3.0
            d3 = (17 * aodp + getS4()) * temp
            d4 = 0.5 * temp * aodp * tsi * (221 * aodp + 31 * getS4()) * c1
            t3cof = d2 + 2 * c1sq
            t4cof = 0.25 * (3.0 * d3 + c1 * (12 * d2 + 10 * c1sq))
            t5cof = 0.2 * (3.0 * d4 + 12 * c1 * d3 + 6 * d2 * d2 + 15 * c1sq * (2.0 * d2 + c1sq))
        }
        sgp4Init = true
    }

    private companion object {
        private const val serialVersionUID = 1206152575764077691L
    }
}
