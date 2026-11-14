package com.subhashh.lumen.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.subhashh.lumen.R
import com.subhashh.lumen.data.LumenPrefs
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.library.LibraryViewModel
import com.subhashh.lumen.ui.onboarding.openSpecial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MediaPermissions = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Full or partial ("Select photos") access both count — partial users just see fewer items. */
private fun Context.hasMediaAccess(): Boolean = MediaPermissions.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun LumenApp(
    /** Set when another app asked Lumen to pick photos or videos. */
    pick: PickRequest? = null,
    onPicked: (List<MediaItem>) -> Unit = {},
    onCancelPick: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel()
    var hasAccess by rememberSaveable { mutableStateOf(context.hasMediaAccess()) }

    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasAccess = context.hasMediaAccess()
        if (hasAccess) {
            viewModel.refresh()
        } else if (activity != null && MediaPermissions.none { activity.shouldShowRequestPermissionRationale(it) }) {
            // Denied twice, Android answers at once without asking, so the button would do nothing:
            // App Info is the only way left. Coming back re-checks access (on resume, below).
            context.openSpecial(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }

    // Re-check on resume: access can change in system settings, and new photos may have been taken.
    LifecycleResumeEffect(Unit) {
        hasAccess = context.hasMediaAccess()
        if (hasAccess) viewModel.refresh()
        onPauseOrDispose { }
    }

    LifecycleStartEffect(hasAccess) {
        viewModel.watchMedia(hasAccess)
        onStopOrDispose { viewModel.watchMedia(false) }
    }

    // A phone whose NPU Lumen AI can use, with models from before NPU support: fetches the NPU's
    // files on Wi-Fi, without asking. Search keeps working and moves over once they're in.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { com.subhashh.lumen.ai.Models.fetchNpuFiles(context) } }

    // First launch: onboarding. Someone who already had access before onboarding existed has
    // been using Lumen, so they skip it.
    val prefs = remember { com.subhashh.lumen.data.LumenPrefs(context) }
    // Read from prefs each time the screen is built (not saved state): Show Onboarding in
    // Developer Options clears it and restarts the activity.
    /** Onboarding just ended: its photos fly into the Library's grid. */
    var entrance by remember { mutableStateOf<com.subhashh.lumen.ui.onboarding.OnboardingExit?>(null) }
    var onboarded by remember {
        mutableStateOf(prefs.onboardingDone ?: context.hasMediaAccess().also { prefs.onboardingDone = it })
    }
    val showOnboarding = !onboarded && pick == null
    Box(Modifier.fillMaxSize()) {
        // One call site for the app whether onboarding is up or not: it's built underneath once
        // access is granted, so when onboarding ends its grid is already laid out and scrolled
        // for the photos to fly into, with nothing to build at that moment.
        if (hasAccess) {
            MainScaffold(viewModel, pick, onPicked, onCancelPick, entrance, onEntranceDone = { entrance = null })
        } else if (!showOnboarding) {
            PermissionScreen(onGrant = { launcher.launch(MediaPermissions) })
        }
        // Another app asking for a photo goes straight to the picker (or the access prompt).
        if (showOnboarding) {
            val items by viewModel.items.collectAsState()
            com.subhashh.lumen.ui.onboarding.Onboarding(
                hasAccess = hasAccess,
                onRequestAccess = { launcher.launch(MediaPermissions) },
                // Oldest to newest, as the grid runs: the newest lands bottom right.
                photos = remember(items) { items.orEmpty().sortedBy { it.takenAt }.takeLast(40) },
                onFinish = { exit ->
                    prefs.onboardingDone = true
                    entrance = exit
                    onboarded = true
                },
            )
        }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.main_permission_message),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.main_allow_access)) }
    }
}
