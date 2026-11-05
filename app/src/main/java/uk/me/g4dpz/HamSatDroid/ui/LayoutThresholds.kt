// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

/**
 * The window widths at which the layout changes. They are the only place these numbers appear, so they can be
 * adjusted after trying the app on the real device.
 *
 * The Galaxy Tab A11 reports 800 x 1340 pixels at 213 dpi, which is 601 dp wide in portrait and 1007 dp wide in
 * landscape, so portrait is just over the first threshold and landscape is well over the second. Other density
 * settings move it (at 240 dpi portrait would be 533 dp).
 */
object LayoutThresholds {
    /** From this width the list and the selected pass are shown side by side. */
    const val TWO_PANE_MIN_WIDTH_DP = 600

    /** From this width the list, the sky chart and the map are all shown. */
    const val DASHBOARD_MIN_WIDTH_DP = 960
}

/** How much of the app is shown at once. */
enum class WindowLayout {
    /** One screen at a time, with navigation. */
    SINGLE_PANE,

    /** The pass list beside the sky view of the selected pass. */
    TWO_PANE,

    /** The pass list beside the sky view and the map. */
    DASHBOARD;

    companion object {
        fun forWidth(widthDp: Int): WindowLayout = when {
            widthDp >= LayoutThresholds.DASHBOARD_MIN_WIDTH_DP -> DASHBOARD
            widthDp >= LayoutThresholds.TWO_PANE_MIN_WIDTH_DP -> TWO_PANE
            else -> SINGLE_PANE
        }
    }
}

/** How the sky chart and the map share the area to the right of the pass list in the dashboard. */
enum class ChartArrangement { SIDE_BY_SIDE, STACKED }

object DashboardLayout {
    /** The smallest size, in dp, at which the sky chart is still useful. */
    const val MIN_SKY_DP = 300f

    /** The narrowest the 2:1 world map may be, in dp, before it is too small to read. */
    const val MIN_MAP_WIDTH_DP = 420f

    const val GAP_DP = 16f

    /**
     * Whether the sky chart and the map fit next to each other in an area of this size, or must be stacked (map above,
     * sky below, scrolling if needed). Side by side needs room for a square sky chart and a map that is not too small.
     */
    fun choose(areaWidthDp: Float, areaHeightDp: Float): ChartArrangement =
        if (areaWidthDp >= MIN_SKY_DP + GAP_DP + MIN_MAP_WIDTH_DP && areaHeightDp >= MIN_SKY_DP) {
            ChartArrangement.SIDE_BY_SIDE
        } else {
            ChartArrangement.STACKED
        }
}
