package com.antiscroll.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramSessionLimitRulesTest {
    private val day = "2026-08-25"
    private val nextMidnight = 100_000_000L

    @Test
    fun `time below five minutes remains available in the current session`() {
        val state = InstagramSessionLimitRules.freshState(day)

        val update = InstagramSessionLimitRules.recordActiveTime(
            state = state,
            deltaMillis = 4L * 60L * 1_000L,
            currentDayKey = day,
            nowMillis = 1_000L,
            nextMidnightMillis = nextMidnight,
        )

        assertEquals(4L * 60L * 1_000L, update.state.sessionUsedMillis)
        assertEquals(0, update.state.violationsToday)
        assertNull(update.startedLockUntilMillis)
    }

    @Test
    fun `first five minute overrun starts a thirty minute block`() {
        val now = 10_000_000L
        val state = InstagramSessionLimitRules.freshState(day).copy(
            sessionUsedMillis = InstagramSessionLimitRules.SESSION_LIMIT_MILLIS - 1_000L,
        )

        val update = InstagramSessionLimitRules.recordActiveTime(
            state = state,
            deltaMillis = 1_000L,
            currentDayKey = day,
            nowMillis = now,
            nextMidnightMillis = nextMidnight,
        )

        assertEquals(1, update.state.violationsToday)
        assertEquals(0L, update.state.sessionUsedMillis)
        assertEquals(
            now + InstagramSessionLimitRules.FIRST_LOCK_DURATION_MILLIS,
            update.startedLockUntilMillis,
        )
    }

    @Test
    fun `second overrun blocks Instagram until midnight`() {
        val state = InstagramSessionLimitRules.freshState(day).copy(
            sessionUsedMillis = InstagramSessionLimitRules.SESSION_LIMIT_MILLIS - 1_000L,
            violationsToday = 1,
        )

        val update = InstagramSessionLimitRules.recordActiveTime(
            state = state,
            deltaMillis = 1_000L,
            currentDayKey = day,
            nowMillis = 40_000_000L,
            nextMidnightMillis = nextMidnight,
        )

        assertEquals(2, update.state.violationsToday)
        assertEquals(nextMidnight, update.state.blockedUntilMillis)
        assertEquals(nextMidnight, update.startedLockUntilMillis)
    }

    @Test
    fun `a new local day resets sessions penalties and locks`() {
        val yesterday = InstagramSessionState(
            dayKey = "2026-08-24",
            sessionUsedMillis = 250_000L,
            violationsToday = 2,
            blockedUntilMillis = 90_000_000L,
        )

        val normalized = InstagramSessionLimitRules.normalize(
            state = yesterday,
            currentDayKey = day,
            nowMillis = 50_000_000L,
        )

        assertEquals(InstagramSessionLimitRules.freshState(day), normalized)
    }

    @Test
    fun `the first penalty never continues beyond midnight`() {
        val now = nextMidnight - 5L * 60L * 1_000L
        val state = InstagramSessionLimitRules.freshState(day).copy(
            sessionUsedMillis = InstagramSessionLimitRules.SESSION_LIMIT_MILLIS,
        )

        val update = InstagramSessionLimitRules.recordActiveTime(
            state = state,
            deltaMillis = 1L,
            currentDayKey = day,
            nowMillis = now,
            nextMidnightMillis = nextMidnight,
        )

        assertEquals(nextMidnight, update.startedLockUntilMillis)
    }
}
