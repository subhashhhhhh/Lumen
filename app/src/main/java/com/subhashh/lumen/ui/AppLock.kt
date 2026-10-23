package com.subhashh.lumen.ui

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.subhashh.lumen.data.HiddenLockStore
import com.subhashh.lumen.data.LumenPrefs

/**
 * App Lock: Lumen asks for its lock (the same one as Hidden) when it's opened, and again after
 * it's been in the background for the chosen time. Shared by every Lumen screen: the app, the
 * photo picker, photos opened from other apps.
 */
object AppLock {

    /** True while Lumen should show its lock instead of its content. */
    var locked by mutableStateOf(false)
        private set

    private var started = 0
    private var backgroundedAt = 0L

    fun install(app: Application) {
        locked = enabled(app)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0 && backgroundedAt != 0L && enabled(activity)) {
                    val away = SystemClock.elapsedRealtime() - backgroundedAt
                    if (away >= LumenPrefs(activity).appLockMinutes * 60_000L) locked = true
                }
                applyRecentsPreview(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                // Rotating the screen stops and restarts the activity; that isn't leaving.
                if (activity.isChangingConfigurations) {
                    started--
                    return
                }
                if (--started == 0) backgroundedAt = SystemClock.elapsedRealtime()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun unlock() {
        locked = false
    }

    /** App Lock just turned on or off in Settings. On doesn't lock the open app, only the next return. */
    fun settingsChanged(activity: Activity) {
        if (!enabled(activity)) locked = false
        applyRecentsPreview(activity)
    }

    private fun enabled(context: android.content.Context) = LumenPrefs(context).appLock && HiddenLockStore(context).isSet

    /** With App Lock on, the recent-apps screen shows a blank card instead of your photos (Android 13+). */
    private fun applyRecentsPreview(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.setRecentsScreenshotEnabled(!enabled(activity))
    }
}
