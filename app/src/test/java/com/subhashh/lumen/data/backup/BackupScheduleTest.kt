package com.subhashh.lumen.data.backup

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

/** Automatic backups: the defaults, and when a scheduled run actually backs up. */
@RunWith(RobolectricTestRunner::class)
class BackupScheduleTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val choices get() = context.getSharedPreferences("lumen-backup-choices", Context.MODE_PRIVATE)
    private val backup get() = context.getSharedPreferences("lumen-backup", Context.MODE_PRIVATE)

    @Before
    fun fresh() {
        choices.edit().clear().commit()
        backup.edit().clear().commit()
        // As Android's start-up library does on the phone; set() queues work through WorkManager.
        runCatching { androidx.work.WorkManager.getInstance(context) }
            .onFailure { androidx.work.WorkManager.initialize(context, androidx.work.Configuration.Builder().build()) }
    }

    private fun lastBackupAgo(hours: Double) {
        backup.edit().putLong("last", System.currentTimeMillis() - (hours * 3_600_000).toLong()).commit()
    }

    @Test
    fun theDefaultsAreDailyWhileChargingOnWifiKeeping30() {
        assertEquals(BackupSchedule.Every.Daily, BackupSchedule.every(context))
        assertTrue(BackupSchedule.charging(context))
        assertTrue(BackupSchedule.wifi(context))
        assertEquals(30, BackupSchedule.keep(context))
    }

    @Test
    fun anUnknownScheduleFromANewerVersionReadsAsDaily() {
        choices.edit().putString("auto", "hourly").commit()
        assertEquals(BackupSchedule.Every.Daily, BackupSchedule.every(context))
    }

    @Test
    fun settingsAreKept() {
        BackupSchedule.set(context, every = BackupSchedule.Every.Weekly, charging = false, wifi = false, keep = 0)
        assertEquals(BackupSchedule.Every.Weekly, BackupSchedule.every(context))
        assertFalse(BackupSchedule.charging(context))
        assertFalse(BackupSchedule.wifi(context))
        assertEquals(0, BackupSchedule.keep(context))
        // Changing one leaves the others.
        BackupSchedule.set(context, keep = 7)
        assertEquals(BackupSchedule.Every.Weekly, BackupSchedule.every(context))
        assertFalse(BackupSchedule.wifi(context))
        assertEquals(7, BackupSchedule.keep(context))
    }

    @Test
    fun neverBackedUpIsDue() {
        assertTrue(BackupSchedule.due(context))
    }

    @Test
    fun dailyIsDueOnceMostOfADayHasPassed() {
        // Android runs periodic work a little early or late: 90% of the interval counts.
        lastBackupAgo(1.0)
        assertFalse(BackupSchedule.due(context))
        lastBackupAgo(21.0)
        assertFalse(BackupSchedule.due(context))
        lastBackupAgo(22.0)
        assertTrue(BackupSchedule.due(context))
        lastBackupAgo(24.0 * 30)
        assertTrue(BackupSchedule.due(context))
    }

    @Test
    fun weeklyWaitsMostOfAWeek() {
        choices.edit().putString("auto", "weekly").commit()
        lastBackupAgo(TimeUnit.DAYS.toHours(6).toDouble())
        assertFalse(BackupSchedule.due(context))
        lastBackupAgo(TimeUnit.DAYS.toHours(7).toDouble() * 0.91)
        assertTrue(BackupSchedule.due(context))
    }

    @Test
    fun offIsNeverDue() {
        choices.edit().putString("auto", "off").commit()
        assertFalse(BackupSchedule.due(context))
        lastBackupAgo(24.0 * 365)
        assertFalse(BackupSchedule.due(context))
    }

    @Test
    fun aClockSetBackDoesntStopBackups() {
        // The last backup "in the future" (the clock was wrong then, or moved back since): a
        // scheduled run must not wait days for the clock to catch up.
        backup.edit().putLong("last", System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3)).commit()
        assertTrue("a last backup dated in the future blocks automatic backups", BackupSchedule.due(context))
    }
}
