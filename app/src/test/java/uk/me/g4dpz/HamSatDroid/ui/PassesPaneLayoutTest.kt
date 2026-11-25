package uk.me.g4dpz.HamSatDroid.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pane rules at the sizes of the devices we test on. Pane = window minus the 16 dp padding on each side. */
class PassesPaneLayoutTest {

    // ---- the pickers ----

    @Test
    fun aPhoneInPortraitStacksThePickers() {
        // Galaxy A16: 411 dp wide, single pane, so the pane is 411 - 32 = 379 dp. This was the clipped label.
        assertTrue(PassesPaneLayout.stackPickers(379f))
    }

    @Test
    fun theTwoPaneAndDashboardPanesStackThePickers() {
        assertTrue(PassesPaneLayout.stackPickers(0.4f * 600f - 32f)) // tablet portrait, 40% of 600 dp
        assertTrue(PassesPaneLayout.stackPickers(0.4f * 891f - 32f)) // phone landscape, 40% of 891 dp
        assertTrue(PassesPaneLayout.stackPickers(0.3f * 1116f - 32f)) // tablet landscape dashboard, 30%
    }

    @Test
    fun aWideSingleWindowStillStacksThreePickers() {
        // a small tablet or an unfolded foldable below the 600 dp two-pane threshold: 528 dp would give each of three
        // pickers 168 dp
        assertTrue(PassesPaneLayout.stackPickers(560f - 32f))
    }

    @Test
    fun theStackingBoundaryIs700() {
        assertTrue(PassesPaneLayout.stackPickers(699.9f))
        assertFalse(PassesPaneLayout.stackPickers(700f))
        assertFalse(PassesPaneLayout.stackPickers(975f)) // the SM-T225 landscape single pane
    }

    // ---- scrolling the whole pane ----

    @Test
    fun aPhoneInLandscapeScrollsTheWholePane() {
        // 411 dp tall less the status bar and app bar leaves about 300 dp; pinned controls would leave the list nothing
        assertTrue(PassesPaneLayout.scrollWholePane(300f))
    }

    @Test
    fun tallPanesKeepTheControlsPinned() {
        assertFalse(PassesPaneLayout.scrollWholePane(1000f)) // tablet portrait
        assertFalse(PassesPaneLayout.scrollWholePane(2200f / 2.625f)) // phone portrait, about 840 dp
    }

    @Test
    fun theScrollBoundaryIs520() {
        assertTrue(PassesPaneLayout.scrollWholePane(519.9f))
                assertFalse(PassesPaneLayout.scrollWholePane(520f))
    }
    @Test
    fun tallControlsThatLeaveTheListTooLittleScrollTheWholePane() {
        // a 680 dp phone: about 560 dp of pane, and stacked pickers with the heading took about 460 dp, leaving the
        // list 100 dp (a tester could not scroll the passes)
        assertTrue(PassesPaneLayout.scrollWholePane(560f, 460f))
        assertFalse(PassesPaneLayout.scrollWholePane(560f, 360f))
    }
    @Test
        fun theListRoomBoundaryIs200AfterTheGap() {
        // 800 dp of pane less 588 dp of controls less the 12 dp gap leaves the list exactly 200 dp
        assertTrue(PassesPaneLayout.scrollWholePane(800f, 588.1f))
        assertFalse(PassesPaneLayout.scrollWholePane(800f, 588f))
    }
    @Test
    fun beforeTheControlsAreMeasuredOnlyThePaneHeightCounts() {
        assertFalse(PassesPaneLayout.scrollWholePane(560f))
    }
}
