package com.subhashh.lumen.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceClassTest {
    private val gb = 1_000_000_000L

    @Test
    fun aPhoneWithAnNpuAndMemoryIsGood() {
        assertEquals(DeviceClass.Good, DeviceClass.of(totalMemory = 7_600_000_000L, is64Bit = true, hasNpu = true))
    }

    @Test
    fun withoutAnNpuItIsFine() {
        assertEquals(DeviceClass.Fine, DeviceClass.of(totalMemory = 11 * gb, is64Bit = true, hasNpu = false))
    }

    @Test
    fun aSixGigabytePhoneIsNotSlow_aFourGigabyteOneIs() {
        // What phones sold as 6 GB and 4 GB report.
        assertEquals(DeviceClass.Fine, DeviceClass.of(totalMemory = 5_600_000_000L, is64Bit = true, hasNpu = false))
        assertEquals(DeviceClass.Slow, DeviceClass.of(totalMemory = 3_700_000_000L, is64Bit = true, hasNpu = false))
    }

    @Test
    fun littleMemoryIsSlowEvenWithAnNpu() {
        assertEquals(DeviceClass.Slow, DeviceClass.of(totalMemory = 3_700_000_000L, is64Bit = true, hasNpu = true))
    }

    @Test
    fun thirtyTwoBitAndroidIsSlowWhateverItsMemory() {
        assertEquals(DeviceClass.Slow, DeviceClass.of(totalMemory = 8 * gb, is64Bit = false, hasNpu = false))
    }
}
