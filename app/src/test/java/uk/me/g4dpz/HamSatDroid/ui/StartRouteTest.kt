// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StartRouteTest {

    @Test
    fun anOrdinaryLaunchOnAFreshInstallOpensTheGuide() {
        assertEquals(Routes.SETUP, StartRoute.choose(requested = null, locationSet = false, finished = false))
    }

    @Test
    fun anOrdinaryLaunchAfterTheGuideWasSkippedOpensThePassesScreen() {
        assertEquals(Routes.PASSES, StartRoute.choose(requested = null, locationSet = false, finished = true))
    }

    @Test
    fun anOrdinaryLaunchWithALocationSetOpensThePassesScreen() {
        // An existing user upgrading: the location is set and the new setting reads false.
        assertEquals(Routes.PASSES, StartRoute.choose(requested = null, locationSet = true, finished = false))
        assertEquals(Routes.PASSES, StartRoute.choose(requested = null, locationSet = true, finished = true))
    }

    @Test
    fun aWidgetTapOpensThePassesScreenEvenOnAFreshInstall() {
        // The widget asks for the passes screen explicitly; it must not be taken for an ordinary launch.
        assertEquals(Routes.PASSES, StartRoute.choose(requested = Routes.PASSES, locationSet = false, finished = false))
    }

    @Test
    fun aNotificationOpensWhatItPointsAt() {
        assertEquals(Routes.SKY, StartRoute.choose(requested = Routes.SKY, locationSet = false, finished = false))
        assertEquals(Routes.SKY, StartRoute.choose(requested = Routes.SKY, locationSet = true, finished = true))
    }
}
