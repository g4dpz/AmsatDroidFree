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
    fun aWideSingleWindowKeepsThePickersSideBySide() {
        // a small tablet or an unfolded foldable below the 600 dp two-pane threshold
        assertFalse(PassesPaneLayout.stackPickers(560f - 32f))
    }

    @Test
    fun theStackingBoundaryIs480() {
        assertTrue(PassesPaneLayout.stackPickers(479.9f))
        assertFalse(PassesPaneLayout.stackPickers(480f))
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
}
