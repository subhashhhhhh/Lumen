package com.subhashh.lumen.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.subhashh.lumen.R
import com.subhashh.lumen.data.LumenPrefs
import kotlinx.coroutines.delay

/** The Lumen Discord's invite: Settings → About links to it too. */
const val DiscordUrl = "https://discord.gg/peT62dhsNy"

/** One prompt a launch at most: the Discord invitation and the star request never follow each other. */
internal object LaunchPrompts {
    var shown = false
}

/**
 * Invites people to the Lumen Discord, once: on the first launch after updating to a version
 * with it, or after onboarding for a new install. A few seconds in, when nothing else is up.
 */
@Composable
fun DiscordRequest(prefs: LumenPrefs, canShow: Boolean, show: (Confirmation) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var due by remember { mutableStateOf(!prefs.discordAsked && prefs.onboardingDone == true && !LaunchPrompts.shown) }
    if (due && canShow) {
        LaunchedEffect(Unit) {
            delay(2_500)
            due = false
            prefs.discordAsked = true
            LaunchPrompts.shown = true
            show(
                Confirmation(
                    title = context.getString(R.string.main_discord_title),
                    message = context.getString(R.string.main_discord_message),
                    confirm = context.getString(R.string.main_discord_join),
                    cancel = context.getString(R.string.main_star_later),
                ) { runCatching { uriHandler.openUri(DiscordUrl) } },
            )
        }
    }
}
