package com.vibeactions.billing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LicenseStatusTest {
    private val day = 24 * 60 * 60_000L
    private val zone = ZoneId.of("Europe/Copenhagen")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    // Trial started Wednesday 18 Nov 2026 at 12:26, so it ends 18 Dec at 12:26.
    private val start = at(2026, 11, 18, 12, 26)

    private fun status(now: Long, lastSeen: Long = 0L, unlocked: Boolean = false, paywall: Boolean = true) =
        licenseStatus(paywall, unlocked, start, lastSeen, now, zone)

    @Test fun firstDayShowsFullTrial() {
        assertEquals(LicenseStatus.Trial(30), status(start))
        assertEquals(LicenseStatus.Trial(30), status(at(2026, 11, 18, 23, 59)))
    }

    @Test fun eachNewCalendarDayCountsOneDown() {
        assertEquals(LicenseStatus.Trial(29), status(at(2026, 11, 19, 0, 1)))
        assertEquals(LicenseStatus.Trial(28), status(at(2026, 11, 20, 8, 19)))
        assertEquals(LicenseStatus.Trial(5), status(at(2026, 12, 13, 18)))
        assertEquals(LicenseStatus.Trial(1), status(at(2026, 12, 17, 23)))
    }

    @Test fun lastDayIsZeroUntilTheTrialEnds() {
        assertEquals(LicenseStatus.Trial(0), status(at(2026, 12, 18, 9)))
        assertEquals(LicenseStatus.Trial(0), status(start + 30 * day - 1))
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
