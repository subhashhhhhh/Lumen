package com.subhashh.lumen.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.subhashh.lumen.R
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.Updater
import kotlinx.coroutines.delay

/** Lumen opened on this many different days (the second day), at least [StarAfterDays] apart, before asking. */
private const val StarDaysUsed = 2
private const val StarAfterDays = 1

/**
 * Asks once for a star on GitHub, from someone who keeps coming back: never on the first days,
 * and never over anything else ([canShow]: no viewer, selection, Settings, picker or other popup).
 * Counts each day Lumen comes to the front.
 */
@Composable
fun StarRequest(prefs: LumenPrefs, canShow: Boolean, show: (Confirmation) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var due by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        prefs.countDayUsed()
        val today = java.time.LocalDate.now().toEpochDay()
        due = !prefs.starAsked && prefs.onboardingDone == true &&
            prefs.daysUsed >= StarDaysUsed && today - prefs.firstDayUsed >= StarAfterDays
        onPauseOrDispose { }
    }
    if (due && canShow) {
        LaunchedEffect(Unit) {
            // Not the moment Lumen opens: a few seconds in. Opening the viewer or Settings meanwhile
            // cancels this, and it waits for the next quiet moment.
            delay(4_000)
            due = false
            prefs.starAsked = true
            show(
                Confirmation(
                    title = context.getString(R.string.main_star_title),
                    message = context.getString(R.string.main_star_message),
                    confirm = context.getString(R.string.main_star_confirm),
                    cancel = context.getString(R.string.main_star_later),
                ) { runCatching { uriHandler.openUri(Updater.RepoUrl) } },
            )
        }
    }
}
