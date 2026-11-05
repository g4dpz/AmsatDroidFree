// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.ui.MainActivity
import uk.me.g4dpz.HamSatDroid.ui.Routes
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The next-pass home-screen widget, built with Glance. It shows the state computed by [WidgetUpdater], follows the
 * light/dark theme and dynamic colour, and opens the app when tapped. Two layouts: a compact one (name and AOS) and a
 * larger one that adds elevation and azimuths, chosen by the available size.
 */
class NextPassWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT, LARGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Computed here (off the main thread) before the content is composed.
        val state = WidgetUpdater.compute(context)
        provideContent {
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }

    @Composable
    private fun WidgetContent(state: WidgetState) {
        val context = LocalContext.current
        val showDetail = LocalSize.current.height >= LARGE.height
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(16.dp)
                .padding(12.dp)
                .clickable(actionStartActivity(MainActivity.intent(context, Routes.PASSES))),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            when (state) {
                WidgetState.NoLocation -> Message(context.getString(R.string.widget_set_location))
                WidgetState.NoSatellite -> Message(context.getString(R.string.widget_no_satellite))
                WidgetState.Error -> Message(context.getString(R.string.widget_error))
                is WidgetState.Ready -> ReadyContent(context, state, showDetail)
            }
        }
    }

    @Composable
    private fun ReadyContent(context: Context, state: WidgetState.Ready, showDetail: Boolean) {
        val pass = state.pass
        Text(
            state.satName,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            context.getString(R.string.widget_next_pass_line, context.getString(R.string.widget_aos_at, clockTime(pass.start)), relativeTime(context, pass.start)),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
        )
        if (showDetail) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                context.getString(R.string.widget_max_el, pass.maxElevationDeg.toInt()),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
            )
            Text(
                context.getString(R.string.widget_az, pass.aosAzimuthDeg, pass.losAzimuthDeg),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
            )
        }
    }

    @Composable
    private fun Message(text: String) {
        Text(text, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
    }

    private fun clockTime(instant: Instant): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(instant)

    private fun relativeTime(context: Context, aos: Instant): String {
        val minutes = Duration.between(Instant.now(), aos).toMinutes()
        return if (minutes <= 0) context.getString(R.string.widget_now)
        else context.getString(R.string.widget_in_minutes, minutes)
    }

    private companion object {
        val COMPACT = DpSize(140.dp, 48.dp)
        val LARGE = DpSize(180.dp, 110.dp)
    }
}
