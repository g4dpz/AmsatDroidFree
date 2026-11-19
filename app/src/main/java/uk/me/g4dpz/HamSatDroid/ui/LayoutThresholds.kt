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
        /**
         * The layout for a window [widthDp] wide. At a large font (see [LargeFont]) the text takes more room, so the
         * width is divided by [fontScale] first: at 200 percent a 601 dp tablet is treated as 300 dp wide and gets one
         * pane, and a 1007 dp one as 503 dp. Below the large-font scale the font has no effect.
         */
        fun forWidth(widthDp: Int, fontScale: Float = 1f): WindowLayout {
            val effective = if (LargeFont.isLarge(fontScale)) (widthDp / fontScale).toInt() else widthDp
            return when {
                effective >= LayoutThresholds.DASHBOARD_MIN_WIDTH_DP -> DASHBOARD
                effective >= LayoutThresholds.TWO_PANE_MIN_WIDTH_DP -> TWO_PANE
                else -> SINGLE_PANE
            }
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

/**
 * How the controls of the passes pane (the satellite and range pickers, start time, Calculate and the heading) are
 * arranged, from the size of the pane they sit in. Phones are narrow and, in landscape, short; tablets have room.
 */
object PassesPaneLayout {
    /**
     * Below this pane width, in dp, the two pickers are stacked, each the full width. Side by side each gets half, and
     * under about 220 dp each the label "Calculate passes for the next" wraps and is clipped and a satellite name such
     * as "OSCAR 7 (AO-7)" is cut off. A phone in portrait has a pane about 380 dp wide.
     */
    const val STACK_PICKERS_BELOW_WIDTH_DP = 480f

    /** The same limit at a large font, in dp of normal-sized text (the width divided by the font scale). */
    const val STACK_PICKERS_LARGE_FONT_BELOW_WIDTH_DP = 600f

    /**
     * Below this pane height, in dp, the controls scroll away with the pass list instead of staying pinned above it.
     * Pinned, they take most of a phone's 411 dp landscape height and leave the list a single line.
     */
    const val SCROLL_WHOLE_PANE_BELOW_HEIGHT_DP = 520f

    /**
     * At a large font (see [LargeFont]) the pane width is divided by [fontScale], as for [WindowLayout.forWidth], and
     * the pickers stack below [STACK_PICKERS_LARGE_FONT_BELOW_WIDTH_DP] of that. A satellite name such as
     * "RS-44 & BREEZE-KM R/B" needs three lines in half of a 975 dp pane at 200 percent (487 dp of normal text), one more
     * than a picker shows, so the large-font limit is higher than the normal one.
     */
    fun stackPickers(paneWidthDp: Float, fontScale: Float = 1f): Boolean =
        if (LargeFont.isLarge(fontScale)) {
            paneWidthDp / fontScale < STACK_PICKERS_LARGE_FONT_BELOW_WIDTH_DP
        } else {
            paneWidthDp < STACK_PICKERS_BELOW_WIDTH_DP
        }

    /**
     * At a large font (see [LargeFont]) the stacked pickers and the heading are taller, so the pane height is divided by
     * [fontScale] first, as the width is for [stackPickers]; at 200 percent the controls scroll with the list in any
     * pane under 1040 dp tall, so they cannot take all of it and hide the list.
     */
    fun scrollWholePane(paneHeightDp: Float, fontScale: Float = 1f): Boolean {
        val effective = if (LargeFont.isLarge(fontScale)) paneHeightDp / fontScale else paneHeightDp
        return effective < SCROLL_WHOLE_PANE_BELOW_HEIGHT_DP
    }
}

/**
 * How the layouts react to a large font size (the system font scale, set in Android's display settings). A floating
 * label that wraps collides with the border of its box, and two fields side by side leave each too little width, so from
 * [LARGE_FONT_SCALE] labels go above their fields and fields that sat side by side are stacked.
 */
object LargeFont {
    const val LARGE_FONT_SCALE = 1.3f

    fun isLarge(fontScale: Float): Boolean = fontScale >= LARGE_FONT_SCALE
}
