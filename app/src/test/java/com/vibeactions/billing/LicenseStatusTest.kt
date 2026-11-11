package com.vibeactions.billing

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseStatusTest {
    private val day = 24 * 60 * 60_000L
    private val start = 1_800_000_000_000L

    private fun status(now: Long, lastSeen: Long = 0L, unlocked: Boolean = false, paywall: Boolean = true) =
        licenseStatus(paywall, unlocked, start, lastSeen, now)

    @Test fun firstDayShowsFullTrial() = assertEquals(LicenseStatus.Trial(30), status(start))

    @Test fun daysLeftRoundsUp() {
        assertEquals(LicenseStatus.Trial(30), status(start + 1))
        assertEquals(LicenseStatus.Trial(1), status(start + 30 * day - 1))
        assertEquals(LicenseStatus.Trial(5), status(start + 25 * day))
    }

    @Test fun endsExactlyAfterThirtyDays() {
        assertEquals(LicenseStatus.Expired, status(start + 30 * day))
        assertEquals(LicenseStatus.Expired, status(start + 400 * day))
    }

    @Test fun windingTheClockBackDoesNotExtendTheTrial() =
        assertEquals(LicenseStatus.Expired, status(now = start + day, lastSeen = start + 31 * day))

    @Test fun purchaseUnlocksEvenAfterExpiry() =
        assertEquals(LicenseStatus.Unlocked, status(start + 90 * day, unlocked = true))

    @Test fun noPaywallIsAlwaysUnlocked() =
        assertEquals(LicenseStatus.Unlocked, status(start + 90 * day, paywall = false))
}
