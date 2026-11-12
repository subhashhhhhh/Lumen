package com.subhashh.lumen.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterVersionTest {

    private fun newer(candidate: String, installed: String) = Updater.isNewer(candidate, installed)

    @Test
    fun comparesNumbersNotText() {
        assertTrue(newer("1.10.0", "1.9.2"))
        assertFalse(newer("1.9.2", "1.10.0"))
        assertTrue(newer("0.10.0", "0.9.99"))
        assertTrue(newer("2.0.0", "1.99.99"))
    }

    @Test
    fun eachPartCounts() {
        assertTrue(newer("0.4.3", "0.4.2"))
        assertTrue(newer("0.5.0", "0.4.2"))
        assertTrue(newer("1.0.0", "0.4.2"))
        assertFalse(newer("0.4.1", "0.4.2"))
        assertFalse(newer("0.3.9", "0.4.2"))
    }

    @Test
    fun sameVersionIsNotAnUpdate() {
        assertFalse(newer("0.4.2", "0.4.2"))
        assertFalse(newer("0.4.2-beta", "0.4.2-beta"))
    }

    @Test
    fun missingPartsCountAsZero() {
        assertFalse(newer("1.0", "1.0.0"))
        assertFalse(newer("1.0.0", "1.0"))
        assertTrue(newer("1.0.1", "1.0"))
        assertFalse(newer("1.0", "1.0.1"))
    }

    @Test
    fun aReleaseBeatsItsPreRelease() {
        assertTrue(newer("1.0.0", "1.0.0-beta"))
        assertFalse(newer("1.0.0-beta", "1.0.0"))
        // The numbers decide first: a later pre-release is still newer.
        assertTrue(newer("1.0.1-beta", "1.0.0"))
        assertFalse(newer("1.0.0-beta", "1.0.1"))
    }

    @Test
    fun anUnknownInstalledVersionTakesTheUpdate() {
        // installedVersion() returns "" when the package manager can't say.
        assertTrue(newer("0.4.2", ""))
    }

    @Test
    fun neverSuggestsADowngradeOrSideways() {
        val versions = listOf("0.1.0", "0.1.7", "0.2.0", "0.4.2", "0.10.0", "1.0.0-beta", "1.0.0", "1.0.1")
        for (i in versions.indices) for (j in versions.indices) {
            val a = versions[i]
            val b = versions[j]
            if (newer(a, b)) assertFalse("both $a > $b and $b > $a", newer(b, a))
            if (i == j) assertFalse("$a is newer than itself", newer(a, b))
            if (i > j) assertTrue("$a should be newer than $b", newer(a, b))
        }
    }
}
