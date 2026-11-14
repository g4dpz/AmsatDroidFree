// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.domain.LocatorSync

/**
 * What to tell the user, while a locator is being typed, about the first character that cannot be right; null while the
 * text can still become a valid locator. It names the position and what that position needs, and for the commonest slip,
 * a zero for the letter O (or the reverse), says so.
 */
@Composable
fun locatorProblemText(text: String): String? {
    val problem = LocatorSync.typingProblem(text) ?: return null
    val what = when (problem.expected) {
        LocatorSync.Expected.LETTER_A_TO_R -> stringResource(R.string.locator_problem_letter_ar, problem.position)
        LocatorSync.Expected.LETTER_A_TO_X -> stringResource(R.string.locator_problem_letter_ax, problem.position)
        LocatorSync.Expected.DIGIT -> stringResource(R.string.locator_problem_digit, problem.position)
        LocatorSync.Expected.NOTHING -> return stringResource(R.string.locator_problem_too_long)
    }
    val hint = when {
        problem.found == '0' && problem.expected != LocatorSync.Expected.DIGIT -> R.string.locator_problem_zero_for_o
        (problem.found == 'O' || problem.found == 'o') && problem.expected == LocatorSync.Expected.DIGIT -> R.string.locator_problem_o_for_zero
        else -> null
    }
    return if (hint == null) what else "$what ${stringResource(hint)}"
}
