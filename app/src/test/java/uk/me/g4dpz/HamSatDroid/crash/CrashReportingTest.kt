// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.crash

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Device
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrashReportingTest {
    @Test
    fun `scrub removes the user, server name, breadcrumbs and the device name but keeps the device model`() {
        val event = SentryEvent().apply {
            user = User().apply { id = "x"; username = "g4dpz"; ipAddress = "203.0.113.9" }
            serverName = "Davids-Tab"
            addBreadcrumb(Breadcrumb("tapped"))
            contexts.setDevice(Device().apply { name = "David's tablet"; id = "fa3db885"; model = "SM-T225" })
        }
        val out = CrashReporting.scrub(event)
        assertNull(out.user)
        assertNull(out.serverName)
        assertNull(out.breadcrumbs)
        assertNull(out.contexts.device?.name)
        assertNull(out.contexts.device?.id)
        assertEquals("SM-T225", out.contexts.device?.model)
    }

    @Test
    fun `scrub copes with an event that has no device context`() {
        CrashReporting.scrub(SentryEvent())
    }
}
