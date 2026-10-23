package com.subhashh.lumen.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code Lumen runs on its hot paths, so Android compiles it ahead of time instead of
 * interpreting/JIT-ing it while the user waits (the viewer's first build was ~75 ms of mostly
 * cold code). Run with the phone connected and unlocked:
 *
 *     ./gradlew :app:generateBaselineProfile
 *
 * This drives the owner's real phone, so it must never touch another app: it never presses Back
 * (which can leave Lumen), closes things with swipe-downs instead, and checks Lumen is in front
 * before every tap or swipe, relaunching it otherwise.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "com.subhashh.lumen",
        includeInStartupProfile = true,
    ) {
        // A fresh install would stop at the permission screen.
        for (permission in listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO")) {
            device.executeShellCommand("pm grant $packageName android.permission.$permission")
        }

        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        val w = device.displayWidth
        val h = device.displayHeight

        // Scroll the library (newest is at the bottom, so scroll up into history).
        repeat(3) {
            inLumen { device.swipe(w / 2, h / 3, w / 2, h * 2 / 3, 8) }
            device.waitForIdle()
        }

        repeat(3) { i ->
            // Open a photo, swipe to the next one, pull up details, then swipe everything closed.
            inLumen { device.click(w / 6 + i * w / 3, h / 2) }
            Thread.sleep(900)
            inLumen { device.swipe(w * 3 / 4, h / 2, w / 4, h / 2, 8) }
            Thread.sleep(600)
            inLumen { device.swipe(w / 2, h * 2 / 3, w / 2, h / 3, 12) } // details sheet up
            Thread.sleep(700)
            inLumen { device.swipe(w / 2, h / 4, w / 2, h / 2, 12) } // sheet down
            Thread.sleep(600)
            inLumen { device.swipe(w / 2, h / 3, w / 2, h * 3 / 4, 10) } // dismiss viewer
            Thread.sleep(900)
        }

        inLumen { device.findObject(By.text("Collections"))?.click() }
        Thread.sleep(900)
        inLumen { device.swipe(w / 2, h * 2 / 3, w / 2, h / 3, 8) }
        device.waitForIdle()
        inLumen { device.findObject(By.text("Library"))?.click() }
        Thread.sleep(600)
    }

    /** Runs [action] only with Lumen in front; otherwise brings Lumen back instead of acting. */
    private fun MacrobenchmarkScope.inLumen(action: () -> Unit) {
        if (device.currentPackageName == packageName) {
            action()
        } else {
            startActivityAndWait()
            device.waitForIdle()
        }
    }
}
