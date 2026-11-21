package com.subhashh.lumen.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.subhashh.lumen.ai.Npu

/**
 * How well this phone runs Lumen, worked out once at first launch. Lumen is built for recent
 * phones: its smart features and glass are heavy, and on phones well below that people saw lag
 * and heat and blamed the app. A [Slow] phone is told so in onboarding, starts with lighter
 * settings, and is reminded in Settings → Smart Features until it's on the lighter models.
 * Nothing is ever locked: the person may know their phone better than these three numbers do.
 */
enum class DeviceClass {
    /** Has an NPU Lumen has models for: smart features run fast and cool. */
    Good,

    /** No such NPU, but enough memory: everything works, the lighter search model is suggested. */
    Fine,

    /** 32-bit Android, or under about 6 GB of memory: below what Lumen is built for. */
    Slow;

    companion object {
        /**
         * Phones sold as "6 GB" report a little under it (5.5 to 5.8), ones sold as "4 GB" about
         * 3.7: this line falls between the two.
         */
        const val SlowBelowBytes = 5_000_000_000L

        fun of(totalMemory: Long, is64Bit: Boolean, hasNpu: Boolean): DeviceClass = when {
            !is64Bit || totalMemory < SlowBelowBytes -> Slow
            hasNpu -> Good
            else -> Fine
        }

        @Volatile
        private var measured: DeviceClass? = null

        /** This phone's class; Developer Options can make it [Slow], to see what such a phone shows. */
        fun of(context: Context): DeviceClass {
            if (LumenPrefs(context).pretendSlowPhone) return Slow
            return measured ?: of(
                totalMemory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem,
                is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
                hasNpu = Npu.chip(context) != null,
            ).also { measured = it }
        }
    }
}
