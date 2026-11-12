package com.subhashh.lumen.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HiddenLockStoreTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val store = HiddenLockStore(context)
    private val raw get() = context.getSharedPreferences("hidden_lock", Context.MODE_PRIVATE).all

    @Test
    fun noLockOpensNothing() {
        assertFalse(store.isSet)
        assertNull(store.type)
        assertFalse(store.verify(""))
        assertFalse(store.verify("1234"))
    }

    @Test
    fun onlyTheExactSecretOpens() {
        store.set(LockType.Pin, "4821")
        assertTrue(store.verify("4821"))
        for (wrong in listOf("", "4820", "482", "48211", " 4821", "4821 ", "1284")) {
            assertFalse("'$wrong' opened it", store.verify(wrong))
        }
    }

    @Test
    fun remembersTypeAndPinLength() {
        store.set(LockType.Pin, "482193")
        assertEquals(LockType.Pin, store.type)
        assertEquals(6, store.pinLength)
        store.set(LockType.Password, "correct horse")
        assertEquals(LockType.Password, store.type)
        assertEquals("only a PIN's length is kept", 0, store.pinLength)
    }

    @Test
    fun passwordsAreCaseSensitiveAndUnicodeSafe() {
        store.set(LockType.Password, "Grüße 東京 🔒")
        assertTrue(store.verify("Grüße 東京 🔒"))
        assertFalse(store.verify("grüße 東京 🔒"))
        assertFalse(store.verify("Gruße 東京 🔒"))
    }

    @Test
    fun theSecretIsNeverStored() {
        val secret = "hunter2-very-secret"
        store.set(LockType.Password, secret)
        for ((key, value) in raw) {
            val text = value.toString()
            assertFalse("$key holds the secret", text.contains(secret))
            assertFalse("$key holds the secret in hex", text.contains(secret.toByteArray().joinToString("") { "%02x".format(it) }))
        }
    }

    @Test
    fun sameSecretIsSaltedDifferentlyEachTime() {
        store.set(LockType.Pin, "1111")
        val first = raw["hash"]
        store.set(LockType.Pin, "1111")
        assertNotEquals(first, raw["hash"])
        assertTrue(store.verify("1111"))
    }

    @Test
    fun changingTheSecretRetiresTheOldOne() {
        store.set(LockType.Pin, "1111")
        store.set(LockType.Pattern, "0-4-8-5")
        assertFalse(store.verify("1111"))
        assertTrue(store.verify("0-4-8-5"))
    }

    @Test
    fun clearRemovesTheLock() {
        store.set(LockType.Pin, "1111")
        store.clear()
        assertFalse(store.isSet)
        assertFalse(store.verify("1111"))
    }

    @Test
    fun damagedStoreOpensNothing() {
        store.set(LockType.Pin, "1111")
        context.getSharedPreferences("hidden_lock", Context.MODE_PRIVATE).edit().putString("hash", "zz").commit()
        assertFalse(runCatching { store.verify("1111") }.getOrDefault(false))
    }

    @Test
    fun fifthWrongTryLocksForThirtySeconds() {
        repeat(HiddenLockStore.MaxAttempts - 1) { store.recordFailure() }
        assertEquals(4, store.failedAttempts())
        assertTrue("no pause before the fifth miss", store.lockedOutUntil() <= System.currentTimeMillis())

        val before = System.currentTimeMillis()
        store.recordFailure()
        val wait = store.lockedOutUntil() - before
        assertTrue("paused ${wait}ms", wait in HiddenLockStore.LockoutMs - 1000..HiddenLockStore.LockoutMs + 1000)
    }

    @Test
    fun successForgetsFailures() {
        repeat(HiddenLockStore.MaxAttempts) { store.recordFailure() }
        store.recordSuccess()
        assertEquals(0, store.failedAttempts())
        assertEquals(0L, store.lockedOutUntil())
        // A fresh run of misses gets its full allowance again.
        repeat(HiddenLockStore.MaxAttempts - 1) { store.recordFailure() }
        assertTrue(store.lockedOutUntil() <= System.currentTimeMillis())
    }

    @Test
    fun failuresSurviveARestart() {
        repeat(3) { store.recordFailure() }
        assertEquals(3, HiddenLockStore(context).failedAttempts())
    }

    /**
     * Throttling exists to stop guessing. Trying every 4-digit PIN (10,000 guesses) must cost
     * more than a day of forced waiting, or someone with the unlocked phone gets into Hidden
     * overnight. The pauses are added up as recordFailure() sets them.
     */
    @Test
    fun guessingEveryFourDigitPinTakesMoreThanADay() {
        var waitedMs = 0L
        repeat(10_000) {
            val before = store.lockedOutUntil()
            store.recordFailure()
            val until = store.lockedOutUntil()
            if (until != before) waitedMs += until - System.currentTimeMillis()
        }
        val hours = waitedMs / 3_600_000.0
        assertTrue("all 10,000 PINs can be tried with only %.1f h of lockout".format(hours), hours >= 24)
    }
}
