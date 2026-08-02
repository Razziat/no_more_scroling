package com.antiscroll.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PunitiveLockRulesTest {
    @Test
    fun `creates a thirty minute lock when no lock is active`() {
        val now = 1_000_000L

        val blockedUntil = PunitiveLockRules.blockedUntil(
            currentBlockedUntilMillis = 0L,
            nowMillis = now,
        )

        assertEquals(now + 30L * 60L * 1_000L, blockedUntil)
    }

    @Test
    fun `opening a locked platform does not extend the punishment`() {
        val currentBlockedUntil = 2_000_000L

        val blockedUntil = PunitiveLockRules.blockedUntil(
            currentBlockedUntilMillis = currentBlockedUntil,
            nowMillis = 1_500_000L,
        )

        assertEquals(currentBlockedUntil, blockedUntil)
    }

    @Test
    fun `remaining time never becomes negative`() {
        assertEquals(
            0L,
            PunitiveLockRules.remainingMillis(
                blockedUntilMillis = 1_000L,
                nowMillis = 2_000L,
            ),
        )
    }
}
