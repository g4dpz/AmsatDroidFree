// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.aim

/** Which direction of the device counts as "where it is pointing" for the aiming aid. */
enum class PointingAxis {
    /** The top edge, as if the device is strapped along an antenna boom or held like a Yagi. */
    TOP_EDGE,

    /** The direction the back of the device faces. */
    BACK
}
