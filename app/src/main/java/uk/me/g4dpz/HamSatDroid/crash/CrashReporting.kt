// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.crash

import android.app.Application
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.android.core.SentryAndroid
import uk.me.g4dpz.HamSatDroid.BuildConfig

/**
 * Anonymous crash reports through Sentry, for the internal test only.
 *
 * Sentry is started by hand (the manifest turns its automatic start off) and only when the user allows crash reports
 * and the build carries a DSN. Without a DSN, as on CI and in a fresh clone, nothing starts. What is sent is the
 * stack trace, the app version and the device model and Android version: no user, no IP address from the app, no
 * breadcrumbs, no screenshots, no performance data and no session tracking. [scrub] removes whatever the SDK adds that
 * could identify a person or their location.
 */
object CrashReporting {
    const val ENVIRONMENT = "internal-testing"

    /** Starts or stops Sentry to match [enabled]. Safe to call repeatedly. */
    fun apply(app: Application, enabled: Boolean, dsn: String = BuildConfig.SENTRY_DSN) {
        if (enabled && dsn.isNotBlank()) {
            if (!Sentry.isEnabled()) {
                SentryAndroid.init(app) { options ->
                    options.dsn = dsn
                    options.release = "${app.packageName}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                    options.environment = ENVIRONMENT
                    options.isSendDefaultPii = false
                    options.isEnableAutoSessionTracking = false
                    options.isSendClientReports = false
                    options.isEnableFramesTracking = false
                    options.isAttachScreenshot = false
                    options.isAttachViewHierarchy = false
                    options.enableAllAutoBreadcrumbs(false)
                    options.setBeforeSend { event, _ -> scrub(event) }
                }
            }
        } else if (Sentry.isEnabled()) {
            Sentry.close()
        }
    }

    /** Removes the user, the server name, breadcrumbs and the device's own name and install id from [event]. */
    fun scrub(event: SentryEvent): SentryEvent {
        event.user = null
        event.serverName = null
        event.breadcrumbs = null
        event.contexts.device?.name = null
        event.contexts.device?.id = null
        return event
    }
}
