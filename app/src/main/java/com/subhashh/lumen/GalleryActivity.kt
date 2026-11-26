package com.subhashh.lumen

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.subhashh.lumen.data.LauncherIcon
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.LumenApp
import com.subhashh.lumen.ui.PickRequest
import com.subhashh.lumen.ui.setLumenContent

/**
 * Lumen's main screen. The Home screen doesn't open it by this name but through one of the
 * aliases in the manifest, one per app icon ([LauncherIcon]).
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showChosenIconInRecents()
        val pick = PickRequest.from(intent)
        // A photo reminder's notification: open its photo (not again when recreated).
        if (savedInstanceState == null) com.subhashh.lumen.data.PhotoReminders.handle(intent)
        // Software Update: once a day, if it's on (Settings → About).
        if (savedInstanceState == null) com.subhashh.lumen.ui.settings.UpdateSession.checkIfDue(applicationContext)
        setLumenContent {
            LumenApp(
                pick = pick,
                onPicked = ::returnPicked,
                onCancelPick = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                },
            )
        }
    }

    @OptIn(com.subhashh.lumen.ai.LumenAiEngine::class)
    override fun onStart() {
        super.onStart()
        // Opening Lumen resets Android's daily time for background indexing as a foreground service.
        com.subhashh.lumen.ai.SmartIndex.appOpened(this)
    }

    // Indexing holds its next photo while a finger is on the screen and just after: scrolling
    // during a first index stays smooth (issue #39).
    @OptIn(com.subhashh.lumen.ai.LumenAiEngine::class)
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        com.subhashh.lumen.ai.SmartIndex.touched()
        return super.dispatchTouchEvent(event)
    }

    // A new app icon goes on once Lumen is out of sight (see LauncherIcon.applyPending).
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) LauncherIcon.applyPending(this)
    }

    // Already open (the notification brings it to the front): same request, through the new intent.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        com.subhashh.lumen.data.PhotoReminders.handle(intent)
    }

    /** Recents draws the activity's own icon, not the alias's, unless told which one is in use. */
    private fun showChosenIconInRecents() {
        val icon = LauncherIcon.chosen(this).icon
        @Suppress("DEPRECATION")
        setTaskDescription(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ActivityManager.TaskDescription.Builder().setIcon(icon).build()
            else ActivityManager.TaskDescription(null, icon),
        )
    }

    /** Hands the chosen items back to the app that asked, with permission to read them. */
    private fun returnPicked(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val clip = ClipData.newUri(contentResolver, "Lumen", items.first().uri)
        items.drop(1).forEach { clip.addItem(ClipData.Item(it.uri)) }
        val result = Intent().apply {
            data = items.first().uri
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}
