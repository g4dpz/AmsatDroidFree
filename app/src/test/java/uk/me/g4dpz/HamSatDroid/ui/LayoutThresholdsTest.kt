package uk.me.g4dpz.HamSatDroid.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutThresholdsTest {

    @Test
    fun narrowWindowsAreSinglePane() {
        for (w in listOf(0, 320, 360, 411, 480, 599)) assertEquals("$w dp", WindowLayout.SINGLE_PANE, WindowLayout.forWidth(w))
    }

    @Test
    fun theTwoPaneBoundaryIsAtSixHundred() {
        assertEquals(WindowLayout.SINGLE_PANE, WindowLayout.forWidth(599))
        assertEquals(WindowLayout.TWO_PANE, WindowLayout.forWidth(600))
        assertEquals(WindowLayout.TWO_PANE, WindowLayout.forWidth(959))
    }

    @Test
    fun theDashboardBoundaryIsAtNineHundredAndSixty() {
        assertEquals(WindowLayout.TWO_PANE, WindowLayout.forWidth(959))
        assertEquals(WindowLayout.DASHBOARD, WindowLayout.forWidth(960))
        assertEquals(WindowLayout.DASHBOARD, WindowLayout.forWidth(1920))
    }

    @Test
    fun theGalaxyTabA11AtItsReportedDensity() {
        // 800 x 1340 px at 213 dpi: 601 x 1007 dp
        assertEquals(WindowLayout.TWO_PANE, WindowLayout.forWidth(601)) // portrait
        assertEquals(WindowLayout.DASHBOARD, WindowLayout.forWidth(1007)) // landscape
    }

    @Test
    fun otherDensitiesOfTheSameScreenGiveOtherLayouts() {
        assertEquals(WindowLayout.SINGLE_PANE, WindowLayout.forWidth(533)) // 240 dpi portrait
        assertEquals(WindowLayout.TWO_PANE, WindowLayout.forWidth(893)) // 240 dpi landscape
        assertEquals(WindowLayout.TWO_PANE, WindowLayout.forWidth(800)) // 160 dpi portrait
        assertEquals(WindowLayout.DASHBOARD, WindowLayout.forWidth(1340)) // 160 dpi landscape
    }

    @Test
    fun typicalPhonesStaySinglePaneAndLandscapePhonesGetTwoPane() {
        assertEquals(WindowLayout.SINGLE_PANE, WindowLayout.forWidth(412)) // phone portrait
        assertEquals(WindowLayout.TWO_PANE, WindowLayout.forWidth(915)) // phone landscape
    }

    // ---- the dashboard's right-hand area ----

    @Test
    fun aRoomyAreaPutsTheChartsSideBySide() {
        assertEquals(ChartArrangement.SIDE_BY_SIDE, DashboardLayout.choose(830f, 700f))
        assertEquals(ChartArrangement.SIDE_BY_SIDE, DashboardLayout.choose(736f, 300f)) // exactly the minimum
    }

    @Test
    fun justBelowTheMinimumTheChartsAreStacked() {
        assertEquals(ChartArrangement.STACKED, DashboardLayout.choose(735.9f, 700f))
        assertEquals(ChartArrangement.STACKED, DashboardLayout.choose(830f, 299.9f))
    }

    @Test
    fun theTabA11LandscapeAreaIsStacked() {
        // 1007 dp wide: list about 30% (302 dp), padding 48 dp, leaving about 657 x 600 dp
        assertEquals(ChartArrangement.STACKED, DashboardLayout.choose(657f, 600f))
    }

    @Test
    fun aVeryWideButShortAreaIsStacked() {
        assertEquals(ChartArrangement.STACKED, DashboardLayout.choose(1500f, 200f))
    }

    @Test
    fun aNarrowTallAreaIsStacked() {
        assertEquals(ChartArrangement.STACKED, DashboardLayout.choose(400f, 1200f))
    }

    @Test
    fun dashboardConstantsAreConsistentWithTheThresholds() {
        // At the smallest dashboard width the list takes 30%, leaving room for stacked charts but not side by side
        val right = LayoutThresholds.DASHBOARD_MIN_WIDTH_DP * 0.7f - 48f
        assertEquals(ChartArrangement.STACKED, DashboardLayout.choose(right, 800f))
    }
}
